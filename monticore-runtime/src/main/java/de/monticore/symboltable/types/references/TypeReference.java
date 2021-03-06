/*
 * ******************************************************************************
 * MontiCore Language Workbench
 * Copyright (c) 2015, MontiCore, All rights reserved.
 *
 * This project is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this project. If not, see <http://www.gnu.org/licenses/>.
 * ******************************************************************************
 */

package de.monticore.symboltable.types.references;

import java.util.List;

import de.monticore.symboltable.references.SymbolReference;
import de.monticore.symboltable.types.TypeSymbol;

/**
 * Represents a symbol type reference and is the super type of all type symbol references.
 * <br />
 * Do not implement this interface directly. Instead, use one of its subtypes.
 *
 * @author Pedram Mir Seyed Nazari
 */
public interface TypeReference<T extends TypeSymbol> extends SymbolReference<T> {

  /**
   * @return a list of the actual type arguments, e.g., in <code>{@literal Set<Integer, String>}</code> a
   * list is returned containing <code>Integer</code> and <code>String</code>.
   */
  List<ActualTypeArgument> getActualTypeArguments();

  /**
   * Sets the actual type arguments of this reference.
   * @param actualTypeArguments the actual type arguments.
   */
  void setActualTypeArguments(List<ActualTypeArgument> actualTypeArguments);


  /**
   * @return The array dimension, e.g., for myArray[][] the dimension is 2. For all non-array
   * types the dimension is 0.
   */
  int getDimension();

  /**
   * Sets the array dimension. For example, the dimension of <code>ar[][]</code> is <code>2</code>.
   * For all non-array types, the dimension is <code>0</code>.
   *
   * @param dimension array dimension
   */
  void setDimension(int dimension);

}
