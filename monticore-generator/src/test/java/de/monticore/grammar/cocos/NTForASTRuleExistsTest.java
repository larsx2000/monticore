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

package de.monticore.grammar.cocos;

import org.junit.BeforeClass;
import org.junit.Test;

import de.monticore.grammar.grammar_withconcepts._cocos.Grammar_WithConceptsCoCoChecker;
import de.se_rwth.commons.logging.Log;

/**
 * Created by
 *
 * @author KH
 */
public class NTForASTRuleExistsTest extends CocoTest{

  private final String CODE = "0xA4021";
  private final String MESSAGE = " There must not exist an AST rule for the nonterminal A" +
          " because there exists no production defining A.";
  private static final Grammar_WithConceptsCoCoChecker checker = new Grammar_WithConceptsCoCoChecker();
  private final String grammar = "cocos.invalid.A4021.A4021";

  @BeforeClass
  public static void disableFailQuick() {
    Log.enableFailQuick(false);
    checker.addCoCo(new NTForASTRuleExists());
  }

  @Test
  public void testInvalid(){
    testInvalidGrammar(grammar, CODE, MESSAGE, checker);
  }


  @Test
  public void testCorrect(){
    testValidGrammar("cocos.valid.ASTRules", checker);
  }

}