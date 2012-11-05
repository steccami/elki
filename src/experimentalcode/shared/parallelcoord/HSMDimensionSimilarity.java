package experimentalcode.shared.parallelcoord;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.math.SinCosTable;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.visualization.projections.ProjectionParallel;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;

/**
 * FIXME: This needs serious TESTING before release. Large parts have been
 * rewritten, but could not be tested at the time of rewriting.
 * 
 * Compute the similarity of dimensions by using a hough transformation.
 * 
 * Reference: <br>
 * <p>
 * A. Tatu, G. Albuquerque, M. Eisemann, P. Bak, H. Theisel, M. A. Magnor, and
 * D. A. Keim.<br />
 * Automated Analytical Methods to Support Visual Exploration of High-
 * Dimensional Data. <br/>
 * IEEEVisualization and Computer Graphics, 2011.
 * </p>
 * 
 * @author Erich Schubert
 * @author Robert Rödler
 */
@Reference(authors = "A. Tatu, G. Albuquerque, M. Eisemann, P. Bak, H. Theisel, M. A. Magnor, and D. A. Keim.", title = "Automated Analytical Methods to Support Visual Exploration of High-Dimensional Data", booktitle = "IEEE Trans. Visualization and Computer Graphics, 2011", url = "http://dx.doi.org/10.1109/TVCG.2010.242")
public class HSMDimensionSimilarity implements DimensionSimilarity<NumberVector<?>> {
  /**
   * Angular resolution. Best if divisible by 4: smaller tables.
   * 
   * The original publication used 50.
   */
  private final static int STEPS = 64;

  /**
   * Precompute sinus and cosinus
   */
  SinCosTable table = SinCosTable.make(STEPS);

  @Override
  public double[][] computeDimensionSimilarites(Relation<? extends NumberVector<?>> relation, ProjectionParallel proj, DBIDs subset) {
    final int dim = RelationUtil.dimensionality(relation);
    final int resolution = 500;
    byte[][][][] pics = new byte[dim][dim][][]; // [resolution][resolution];

    // Initialize / allocate "pictures":
    for (int i = 0; i < dim - 1; i++) {
      for (int j = i + 1; j < dim; j++) {
        pics[i][j] = new byte[resolution][resolution];
      }
    }
    // Iterate dataset
    for (DBIDIter id = subset.iter(); id.valid(); id.advance()) {
      double[] pvec = proj.fastProjectDataToRenderSpace(relation.get(id));
      for (int i = 0; i < dim - 1; i++) {
        for (int j = i + 1; j < dim; j++) {
          double xi = pvec[i] / StyleLibrary.SCALE; // to 0..1
          double xj = pvec[j] / StyleLibrary.SCALE; // to 0..1
          drawLine(0, (int) (resolution * xi), resolution - 1, (int) (resolution * xj), pics[i][j]);
        }
      }
    }

    final double stepsq = (double) STEPS * (double) STEPS;
    double[][] mat = new double[dim][dim];
    for (int i = 0; i < dim - 1; i++) {
      for (int j = i + 1; j < dim; j++) {
        int[][] hough = houghTransformation(pics[i][j]);
        pics[i][j] = null; // Release picture
        // The original publication said "median", but judging from the text,
        // meant "mean". Otherwise, always half of the cells are above the
        // threshold, which doesn't match the explanation there.
        double mean = sumMatrix(hough) / stepsq;
        int abovemean = countAboveThreshold(hough, mean);

        mat[i][j] = 1. - (abovemean / stepsq);
        mat[j][i] = 1. - (abovemean / stepsq);
      }
    }
    return mat;
  }

  /**
   * Compute the sum of a matix.
   * 
   * @param mat Matrix
   * @return Sum of all elements
   */
  private long sumMatrix(int[][] mat) {
    long ret = 0;
    for (int i = 0; i < mat[0].length; i++) {
      for (int j = 0; j < mat.length; j++) {
        ret += mat[i][j];
      }
    }
    return ret;
  }

  /**
   * Count the number of cells above the threshold.
   * 
   * @param mat Matrix
   * @param threshold Threshold
   * @return Number of elements above the threshold.
   */
  private int countAboveThreshold(int[][] mat, double threshold) {
    int ret = 0;
    for (int i = 0; i < mat.length; i++) {
      int[] row = mat[i];
      for (int j = 0; j < row.length; j++) {
        if (row[j] >= threshold) {
          ret++;
        }
      }
    }
    return ret;
  }

  /**
   * Perform a hough transformation on the binary image in "mat".
   * 
   * @param mat Binary image
   * @return Hough transformation of image.
   */
  private int[][] houghTransformation(byte[][] mat) {
    final int xres = mat.length, yres = mat[0].length;
    final double tscale = STEPS / Math.sqrt(xres * xres + yres * yres);
    final int[][] ret = new int[STEPS][STEPS];

    for (int x = 0; x < mat.length; x++) {
      for (int y = 0; y < mat[0].length; y++) {
        if (mat[x][y] > 0) {
          for (int i = 0; i < STEPS; i++) {
            final int d = (int) (tscale * (x * table.cos(i) + y * table.sin(i)));
            if (d > 0 && d < STEPS) {
              ret[d][i] += mat[x][y];
            }
          }
        }
      }
    }

    return ret;
  }

  /**
   * Draw a line onto the array, using the classic Bresenham algorithm.
   * 
   * @param x0 Start X
   * @param y0 Start Y
   * @param x1 End X
   * @param y1 End Y
   * @param pic Picture array
   */
  private static void drawLine(int x0, int y0, int x1, int y1, byte[][] pic) {
    final int xres = pic.length, yres = pic[0].length;
    // Ensure bounds
    y0 = (y0 < 0) ? 0 : (y0 >= yres) ? (yres - 1) : y0;
    y1 = (y1 < 0) ? 0 : (y1 >= yres) ? (yres - 1) : y1;
    x0 = (x0 < 0) ? 0 : (x0 >= xres) ? (xres - 1) : x0;
    x1 = (x1 < 0) ? 0 : (x1 >= xres) ? (xres - 1) : x1;
    // Default slope
    final int dx = +Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
    final int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
    // Error counter
    int err = dx + dy;

    for (;;) {
      pic[x0][y0] = 1;
      if (x0 == x1 && y0 == y1) {
        break;
      }

      final int e2 = err << 1;
      if (e2 > dy) {
        err += dy;
        x0 += sx;
      }
      if (e2 < dx) {
        err += dx;
        y0 += sy;
      }
    }
  }
}