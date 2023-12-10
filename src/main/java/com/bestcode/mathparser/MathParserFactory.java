/**********************************************************************
*
*  PRODUCT: Bestcode Math Parser
*  COPYRIGHT:  (C) COPYRIGHT Suavi Ali Demir 2000-2010
*
*  The source code for this program is not GNU or is not free. 
*  Source code is distributed with the math parser binaries to improve
*  maintanability of the software and the user of the software is not
*  granted rights to modify and use source code to create a 
*  competitive product. 
*
*  The user is granted rights to modify and recompile source code 
*  to fix bugs/incompatibilities in the binaries which user has purchased.
*  
*  Resulting binaries cannot be re-sold as a competitive product but they 
*  can be used solely to replace the original binaries.
*  
*  Copyright and all rights of this software, irrespective of what 
*  has been deposited with the U.S. Copyright Office belongs 
*  to Suavi Ali Demir.
* 
***********************************************************************/
package com.bestcode.mathparser;

  /**
    MathParserFactory is used to create an implementation of
    IMathParser interface. This is the main parser engine.
  */
  abstract public class MathParserFactory {
    private MathParserFactory(){}
    /**
     * Creates the object that implements IMathParser interface.
     * If you wish to extend the parser implementation by deriving
     * from the implementation, current implementation class name is 
     * com.bestcode.mathparser.MathParserImpl. <br><br>
     * However, for any unforeseen reason that name or 
     * constructor can change. If you change the original implementation, please
     * rename the package, so that when deployed, there is no conflict with other
     * user's classpaths that contain the original implementation.
     * Bestcode will try to make sure future versions will not break older ones.
     */
    static public IMathParser create(){
      return new MathParserImpl();
    }
  }
