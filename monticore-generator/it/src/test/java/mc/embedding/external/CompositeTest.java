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

package mc.embedding.external;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;

import de.monticore.io.paths.ModelPath;
import de.monticore.symboltable.GlobalScope;
import de.monticore.symboltable.ResolverConfiguration;
import mc.GeneratorIntegrationsTest;
import mc.embedding.external.composite._symboltable.CompositeLanguage;
import mc.embedding.external.composite._symboltable.Text2ContentAdapter;
import mc.embedding.external.embedded._symboltable.TextSymbol;
import mc.embedding.external.host._symboltable.ContentSymbol;
import mc.embedding.external.host._symboltable.HostSymbol;
import org.junit.Test;

public class CompositeTest extends GeneratorIntegrationsTest {

  @Test
  public void test() {
    final CompositeLanguage language = new CompositeLanguage();
    final ResolverConfiguration resolverConfiguration = new ResolverConfiguration();
    resolverConfiguration.addTopScopeResolvers(language.getResolvers());

    final ModelPath modelPath = new ModelPath(Paths.get("src/test/resources/mc/embedding"));

    final GlobalScope scope = new GlobalScope(modelPath, language, resolverConfiguration);

    // Symbol of the host language
    final HostSymbol hostSymbol = scope.<HostSymbol>resolve("ZComposite", HostSymbol.KIND).orElse(null);
    assertNotNull(hostSymbol);
    assertEquals("ZComposite", hostSymbol.getName());

    // Symbol of the embedded language
    final TextSymbol textSymbol = hostSymbol.getSpannedScope().<TextSymbol>resolve("Hello", TextSymbol.KIND).orElse(null);
    assertNotNull(textSymbol);

    // Adapted text symbol -> content symbol
    final ContentSymbol text2ContentSymbol = hostSymbol.getSpannedScope().<ContentSymbol>resolve("Hello", ContentSymbol.KIND).orElse(null);
    assertNotNull(text2ContentSymbol);
    assertTrue(text2ContentSymbol instanceof Text2ContentAdapter);



  }

}
