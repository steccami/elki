package de.lmu.ifi.dbs.elki.database.ids.integer;
/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2012
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

import gnu.trove.list.TIntList;

/**
 * Class accessing a trove int array.
 * 
 * @author Erich Schubert
 */
class TroveArrayStaticDBIDs extends TroveArrayDBIDs implements IntegerArrayStaticDBIDs {
  /**
   * Actual trove store.
   */
  private final TIntList store;

  /**
   * Constructor.
   * 
   * @param store Actual trove store.
   */
  protected TroveArrayStaticDBIDs(TIntList store) {
    super();
    this.store = store;
  }

  @Override
  protected TIntList getStore() {
    return store;
  }
}
