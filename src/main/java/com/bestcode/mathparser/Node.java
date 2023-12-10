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

//generic node, base for all nodes that are declared in implementation section
abstract class Node implements IParameter {
	abstract public double getValue();

	abstract public boolean isUsed(Object Addr);

	// Optimize evaluates constant values at compile time.
	abstract public void optimize();
}
