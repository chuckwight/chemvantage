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
  IParameter is used to supply a parameter value for a user defined function.
  All functions take an IParameter that enables them to query the current value
  of the parameter using the getValue() function.<br><br>
  Example:<br><br>
  class _cosh extends OneParamFunc {<br>
    &nbsp;&nbsp;&nbsp;&nbsp; public double run(IParameter[] p){<br>
    &nbsp;&nbsp;&nbsp;&nbsp; //better to call p[0].getValue() once, it can be costly:<br>
    &nbsp;&nbsp;&nbsp;&nbsp; double x_ = p[0].getValue();<br>
    &nbsp;&nbsp;&nbsp;&nbsp; return (Math.exp(x_)+Math.exp(-x_))*0.5;<br>
    &nbsp;&nbsp;&nbsp;&nbsp; }<br>
  }<br>
  */
  public interface IParameter {
    public double getValue();
  }
  