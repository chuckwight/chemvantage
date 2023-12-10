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
 *  IVariableResolver is implemented by the user and used in
 *  IMathParser.setVariableResolver(IVariableResolver) to enable the 
 *  Math Parser allow variables that are not defined before parse time.
 *  if set, IVariableResolver will be used to return the values of variables
 *  at evalutaion time. This is typically needed when the problem domain 
 *  is too large to define all possible variables ahead of time.
 *  When IMathParser.setVariableResolver is set, parser will tolerate
 *  undefined variables at parse time and it will invoke IVariableResolver
 *  to retrieve variable values at evaluation time.   
 */
public interface IVariableResolver {
	/**
	 * getValue function is implemented by the user and it is called by 
	 * the parser to get the value of an undefined variable at evaluation time.
	 * @param parser - current parser instance
	 * @param varName - variable whose value is being asked for
	 * @return value of the variable named varName
	 */
	public double getValue(IMathParser parser, String varName);
}
