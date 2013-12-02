package experimentalcode.erich.parallel;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.clustering.kmeans.AbstractKMeans;
import de.lmu.ifi.dbs.elki.algorithm.clustering.kmeans.KMeansInitialization;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.KMeansModel;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableIntegerDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDoubleDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.IndefiniteProgress;
import experimentalcode.erich.parallel.mapper.KMeansMapper;

/**
 * Parallel implementation of k-Means clustering.
 * 
 * @author Erich Schubert
 * 
 * @param <V> Vector type
 * @param <D> Distance type
 */
public class ParallelLloydKMeans<V extends NumberVector<?>, D extends NumberDistance<D, ?>> extends AbstractKMeans<V, D, KMeansModel<V>> {
  /**
   * Constructor.
   * 
   * @param distanceFunction Distance function
   * @param k K parameter
   */
  public ParallelLloydKMeans(PrimitiveDistanceFunction<? super NumberVector<?>, D> distanceFunction, int k, int maxiter, KMeansInitialization<V> initializer) {
    super(distanceFunction, k, maxiter, initializer);
  }

  /**
   * Class logger
   */
  private static final Logging LOG = Logging.getLogger(ParallelLloydKMeans.class);

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(getDistanceFunction().getInputTypeRestriction());
  }

  @Override
  public Clustering<KMeansModel<V>> run(Database database, Relation<V> relation) {
    DBIDs ids = relation.getDBIDs();

    // Choose initial means
    List<? extends NumberVector<?>> means = initializer.chooseInitialMeans(database, relation, k, getDistanceFunction());

    // Store for current cluster assignment.
    WritableIntegerDataStore assignment = DataStoreUtil.makeIntegerStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, -1);
    // FIXME: allow non-double distance functions?
    @SuppressWarnings("unchecked")
    final PrimitiveDoubleDistanceFunction<? super NumberVector<?>> dist = (PrimitiveDoubleDistanceFunction<? super NumberVector<?>>) distanceFunction;
    KMeansMapper<V> kmm = new KMeansMapper<>(relation, dist, assignment);

    IndefiniteProgress prog = LOG.isVerbose() ? new IndefiniteProgress("K-Means iteration", LOG) : null;
    for (int iteration = 0; maxiter <= 0 || iteration < maxiter; iteration++) {
      if (prog != null) {
        prog.incrementProcessed(LOG);
      }
      kmm.nextIteration(means);
      ParallelMapExecutor.run(ids, kmm);
      // Stop if no cluster assignment changed.
      if (!kmm.changed()) {
        break;
      }
      means = kmm.getMeans();
    }
    if (prog != null) {
      prog.setCompleted(LOG);
    }

    // Wrap result
    List<ModifiableDBIDs> clusters = new ArrayList<>();
    for (int i = 0; i < k; i++) {
      clusters.add(DBIDUtil.newHashSet((int) (relation.size() * 2. / k)));
    }
    for (DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      clusters.get(assignment.intValue(iter)).add(iter);
    }

    final NumberVector.Factory<V, ?> factory = RelationUtil.getNumberVectorFactory(relation);
    Clustering<KMeansModel<V>> result = new Clustering<>("k-Means Clustering", "kmeans-clustering");
    for (int i = 0; i < clusters.size(); i++) {
      KMeansModel<V> model = new KMeansModel<>(factory.newNumberVector(means.get(i).getColumnVector().getArrayRef()));
      result.addToplevelCluster(new Cluster<>(clusters.get(i), model));
    }
    return result;
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   * 
   * @param <V> Vector type
   * @param <D> Distance type
   */
  public static class Parameterizer<V extends NumberVector<?>, D extends NumberDistance<D, ?>> extends AbstractKMeans.Parameterizer<V, D> {
    @Override
    protected Logging getLogger() {
      return LOG;
    }

    @Override
    protected ParallelLloydKMeans<V, D> makeInstance() {
      return new ParallelLloydKMeans<>(distanceFunction, k, maxiter, initializer);
    }
  }
}