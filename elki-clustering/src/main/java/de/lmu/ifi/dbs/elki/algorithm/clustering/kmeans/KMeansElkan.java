/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2018
 * ELKI Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.lmu.ifi.dbs.elki.algorithm.clustering.kmeans;

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.clustering.kmeans.initialization.KMeansInitialization;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.KMeansModel;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.*;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.NumberVectorDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.SquaredEuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.IndefiniteProgress;
import de.lmu.ifi.dbs.elki.logging.statistics.LongStatistic;
import de.lmu.ifi.dbs.elki.logging.statistics.StringStatistic;
import de.lmu.ifi.dbs.elki.math.linearalgebra.VMath;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;

import net.jafama.FastMath;

/**
 * Elkan's fast k-means by exploiting the triangle inequality.
 * <p>
 * This variant needs O(n*k) additional memory to store bounds.
 * <p>
 * See {@link KMeansHamerly} for a close variant that only uses O(n*2)
 * additional memory for bounds.
 * <p>
 * Reference:
 * <p>
 * C. Elkan<br>
 * Using the triangle inequality to accelerate k-means<br>
 * Proc. 20th International Conference on Machine Learning, ICML 2003
 *
 * @author Erich Schubert
 * @since 0.7.0
 *
 * @apiviz.has KMeansModel
 *
 * @param <V> vector datatype
 */
@Reference(authors = "C. Elkan", //
    title = "Using the triangle inequality to accelerate k-means", //
    booktitle = "Proc. 20th International Conference on Machine Learning, ICML 2003", //
    url = "http://www.aaai.org/Library/ICML/2003/icml03-022.php", //
    bibkey = "DBLP:conf/icml/Elkan03")
public class KMeansElkan<V extends NumberVector> extends AbstractKMeans<V, KMeansModel> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(KMeansElkan.class);

  /**
   * Key for statistics logging.
   */
  private static final String KEY = KMeansElkan.class.getName();

  /**
   * Flag whether to compute the final variance statistic.
   */
  protected boolean varstat = false;

  /**
   * Constructor.
   *
   * @param distanceFunction distance function
   * @param k k parameter
   * @param maxiter Maxiter parameter
   * @param initializer Initialization method
   * @param varstat Compute the variance statistic
   */
  public KMeansElkan(NumberVectorDistanceFunction<? super V> distanceFunction, int k, int maxiter, KMeansInitialization<? super V> initializer, boolean varstat) {
    super(distanceFunction, k, maxiter, initializer);
    this.varstat = varstat;
  }

  @Override
  public Clustering<KMeansModel> run(Database database, Relation<V> relation) {
    if(relation.size() <= 0) {
      return new Clustering<>("k-Means Clustering", "kmeans-clustering");
    }
    // Choose initial means
    LOG.statistics(new StringStatistic(KEY + ".initialization", initializer.toString()));
    double[][] means = initializer.chooseInitialMeans(database, relation, k, getDistanceFunction());
    // Setup cluster assignment store
    List<ModifiableDBIDs> clusters = new ArrayList<>();
    for(int i = 0; i < k; i++) {
      clusters.add(DBIDUtil.newHashSet((int) (relation.size() * 2. / k)));
    }
    WritableIntegerDataStore assignment = DataStoreUtil.makeIntegerStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, -1);
    // Elkan bounds
    WritableDoubleDataStore upper = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, Double.POSITIVE_INFINITY);
    WritableDataStore<double[]> lower = DataStoreUtil.makeStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, double[].class);
    for(DBIDIter it = relation.iterDBIDs(); it.valid(); it.advance()) {
      lower.put(it, new double[k]); // Filled with 0.
    }
    // Storage for updated means:
    final int dim = means[0].length;
    double[][] sums = new double[k][dim], newmeans = new double[k][dim];

    // Cluster separation
    double[] sep = new double[k];
    // Cluster distances
    double[][] cdist = new double[k][k];

    IndefiniteProgress prog = LOG.isVerbose() ? new IndefiniteProgress("K-Means iteration", LOG) : null;
    LongStatistic rstat = LOG.isStatistics() ? new LongStatistic(this.getClass().getName() + ".reassignments") : null;
    LongStatistic diststat = LOG.isStatistics() ? new LongStatistic(KEY + ".distance-computations") : null;
    int iteration = 0;
    for(; maxiter <= 0 || iteration < maxiter; iteration++) {
      LOG.incrementProcessed(prog);
      int changed;
      if(iteration == 0) {
        changed = initialAssignToNearestCluster(relation, means, sums, clusters, assignment, upper, lower, diststat);
      }
      else {
        recomputeSeperation(means, sep, cdist, diststat); // #1
        changed = assignToNearestCluster(relation, means, sums, clusters, assignment, sep, cdist, upper, lower, diststat);
      }
      LOG.statistics(rstat != null ? rstat.setLong(changed) : null);
      // Stop if no cluster assignment changed.
      if(changed == 0) {
        break;
      }
      // Recompute means.
      for(int i = 0; i < k; i++) {
        VMath.overwriteTimes(newmeans[i], sums[i], 1. / clusters.get(i).size());
      }
      movedDistance(means, newmeans, sep); // Overwrites sep
      updateBounds(relation, assignment, upper, lower, sep);
      for(int i = 0; i < k; i++) {
        System.arraycopy(newmeans[i], 0, means[i], 0, dim);
      }
    }
    LOG.setCompleted(prog);
    LOG.statistics(new LongStatistic(KEY + ".iterations", iteration));
    LOG.statistics(diststat);
    upper.destroy();
    lower.destroy();

    return buildResult(clusters, means, varstat, relation, diststat);
  }

  /**
   * Perform initial cluster assignment.
   *
   * @param relation Data
   * @param means Current means
   * @param sums Running sums of the new means
   * @param clusters Current clusters
   * @param assignment Cluster assignment
   * @param upper Upper bounds
   * @param lower Lower bounds
   * @param diststat Distance statistics
   * @return Number of changes (i.e. relation size)
   */
  protected int initialAssignToNearestCluster(Relation<V> relation, double[][] means, double[][] sums, List<ModifiableDBIDs> clusters, WritableIntegerDataStore assignment, WritableDoubleDataStore upper, WritableDataStore<double[]> lower, LongStatistic diststat) {
    assert (k == means.length);
    final boolean issquared = distanceFunction.isSquared();
    for(DBIDIter it = relation.iterDBIDs(); it.valid(); it.advance()) {
      V fv = relation.get(it);
      double[] l = lower.get(it);
      // Check all (other) means:
      double best = Double.POSITIVE_INFINITY;
      int minIndex = -1;
      for(int j = 0; j < k; j++) {
        double dist = distanceFunction.distance(fv, DoubleVector.wrap(means[j]));
        dist = issquared ? FastMath.sqrt(dist) : dist;
        l[j] = dist;
        if(dist < best) {
          minIndex = j;
          best = dist;
        }
      }
      // Assign to nearest cluster.
      clusters.get(minIndex).add(it);
      assignment.putInt(it, minIndex);
      upper.putDouble(it, best);
      plusEquals(sums[minIndex], fv);
    }
    if(diststat != null) {
      diststat.increment(k * relation.size());
    }
    return relation.size();
  }

  /**
   * Reassign objects, but avoid unnecessary computations based on their bounds.
   *
   * @param relation Data
   * @param means Current means
   * @param sums New means as running sums
   * @param clusters Current clusters
   * @param assignment Cluster assignment
   * @param sep Separation of means
   * @param cdist Center-to-center distances
   * @param upper Upper bounds
   * @param lower Lower bounds
   * @param diststat Distance statistics
   * @return true when the object was reassigned
   */
  protected int assignToNearestCluster(Relation<V> relation, double[][] means, double[][] sums, List<ModifiableDBIDs> clusters, WritableIntegerDataStore assignment, double[] sep, double[][] cdist, WritableDoubleDataStore upper, WritableDataStore<double[]> lower, LongStatistic diststat) {
    assert (k == means.length);
    final boolean issquared = distanceFunction.isSquared();
    int changed = 0, dists = 0;
    for(DBIDIter it = relation.iterDBIDs(); it.valid(); it.advance()) {
      final int orig = assignment.intValue(it);
      double u = upper.doubleValue(it);
      // Upper bound check (#2):
      if(u <= sep[orig]) {
        continue;
      }
      boolean recompute_u = true; // Elkan's r(x)
      V fv = relation.get(it);
      double[] l = lower.get(it);
      // Check all (other) means:
      int cur = orig;
      for(int j = 0; j < k; j++) {
        if(orig == j || u <= l[j] || u <= cdist[cur][j]) {
          continue; // Condition #3 i-iii not satisfied
        }
        if(recompute_u) { // Need to update bound? #3a
          u = distanceFunction.distance(fv, DoubleVector.wrap(means[cur]));
          ++dists;
          u = issquared ? FastMath.sqrt(u) : u;
          upper.putDouble(it, u);
          recompute_u = false; // Once only
          if(u <= l[j] || u <= cdist[cur][j]) { // #3b
            continue;
          }
        }
        double dist = distanceFunction.distance(fv, DoubleVector.wrap(means[j]));
        ++dists;
        dist = issquared ? FastMath.sqrt(dist) : dist;
        l[j] = dist;
        if(dist < u) {
          cur = j;
          u = dist;
        }
      }
      // Object is to be reassigned.
      if(cur != orig) {
        upper.putDouble(it, u); // Remember bound.
        clusters.get(cur).add(it);
        clusters.get(orig).remove(it);
        assignment.putInt(it, cur);
        plusMinusEquals(sums[cur], sums[orig], fv);
        ++changed;
      }
    }
    if(diststat != null) {
      diststat.increment(dists);
    }
    return changed;
  }

  /**
   * Update the bounds for k-means.
   *
   * @param relation Relation
   * @param assignment Cluster assignment
   * @param upper Upper bounds
   * @param lower Lower bounds
   * @param move Movement of centers
   */
  protected void updateBounds(Relation<V> relation, WritableIntegerDataStore assignment, WritableDoubleDataStore upper, WritableDataStore<double[]> lower, double[] move) {
    for(DBIDIter it = relation.iterDBIDs(); it.valid(); it.advance()) {
      upper.increment(it, move[assignment.intValue(it)]);
      VMath.minusEquals(lower.get(it), move);
    }
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   *
   * @author Erich Schubert
   *
   * @apiviz.exclude
   */
  public static class Parameterizer<V extends NumberVector> extends AbstractKMeans.Parameterizer<V> {
    @Override
    protected Logging getLogger() {
      return LOG;
    }

    @Override
    protected void getParameterDistanceFunction(Parameterization config) {
      super.getParameterDistanceFunction(config);
      if(distanceFunction instanceof SquaredEuclideanDistanceFunction) {
        return; // Proper choice.
      }
      if(distanceFunction != null && !distanceFunction.isMetric()) {
        LOG.warning("Elkan k-means requires a metric distance, and k-means should only be used with squared Euclidean distance!");
      }
    }

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      super.getParameterVarstat(config);
    }

    @Override
    protected KMeansElkan<V> makeInstance() {
      return new KMeansElkan<>(distanceFunction, k, maxiter, initializer, varstat);
    }
  }
}
