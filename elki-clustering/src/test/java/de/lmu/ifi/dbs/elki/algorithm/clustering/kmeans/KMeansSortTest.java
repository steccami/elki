/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2017
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

import org.junit.Test;

import de.lmu.ifi.dbs.elki.algorithm.clustering.AbstractClusterAlgorithmTest;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.utilities.ELKIBuilder;

/**
 * Regression test for Sort-means.
 *
 * @author Erich Schubert
 * @since 0.7.5
 */
public class KMeansSortTest extends AbstractClusterAlgorithmTest {
  /**
   * Run KMeans with fixed parameters and compare the result to a golden
   * standard.
   */
  @Test
  public void testKMeansSort() {
    Database db = makeSimpleDatabase(UNITTEST + "different-densities-2d-no-noise.ascii", 1000);
    Clustering<?> result = new ELKIBuilder<KMeansSort<DoubleVector>>(KMeansSort.class) //
        .with(KMeans.K_ID, 5) //
        .with(KMeans.SEED_ID, 7) //
        .build().run(db);
    testFMeasure(db, result, 0.998005);
    testClusterSizes(result, new int[] { 199, 200, 200, 200, 201 });
  }
}
