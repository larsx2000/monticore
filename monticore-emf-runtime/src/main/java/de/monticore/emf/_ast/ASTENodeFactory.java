package de.monticore.emf._ast;

import org.eclipse.emf.ecore.EFactory;

public interface ASTENodeFactory extends EFactory {
  // The singleton instance of the factory.
  ASTENodeFactory eINSTANCE = de.monticore.emf._ast.ASTENodeFactoryImpl.init();
  
  // Returns the package supported by this factory.
  ASTENodePackage getASTENodePackage();
}
