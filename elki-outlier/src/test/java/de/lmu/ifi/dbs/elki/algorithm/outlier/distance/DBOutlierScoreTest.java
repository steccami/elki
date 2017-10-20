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
package de.lmu.ifi.dbs.elki.algorithm.outlier.distance;

import org.junit.Test;

import de.lmu.ifi.dbs.elki.algorithm.outlier.AbstractOutlierAlgorithmTest;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.ELKIBuilder;

/**
 * Tests the DBOutlierScore algorithm.
 * 
 * @author Lucia Cichella
 * @since 0.4.0
 */
public class DBOutlierScoreTest extends AbstractOutlierAlgorithmTest {
  @Test
  public void testDBOutlierScore() {
    Database db = makeSimpleDatabase(UNITTEST + "outlier-fire.ascii", 1025);

    DBOutlierScore<DoubleVector> dbOutlierScore = new ELKIBuilder<DBOutlierScore<DoubleVector>>(DBOutlierScore.class) //
        .with(DBOutlierScore.Parameterizer.D_ID, 0.175).build();

    // run DBOutlierScore on database
    OutlierResult result = dbOutlierScore.run(db);

    testSingleScore(result, 1025, 0.688780487804878);
    testAUC(db, "Noise", result, 0.992565641);
  }
}
