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
OneParamFunc abstract class represents a user defined callback function that takes one
parameter. This is a convenience implementation of IFunction interface.
During evaluation of an expression, MathParser calls
OneParamFunc.run(IParameter[] p)
to request the value of the function when it takes p[0] as parameter.

For example, a user could define a function like this:
<br><br>
public class MyFunc extends OneParamFunc {<br>
    &nbsp;&nbsp;  public double run(IParameter[] p){<br>
    &nbsp;&nbsp;&nbsp;&nbsp;     return do_something_with_x( p[0] );<br>
    &nbsp;&nbsp;  }<br>
}<br>
<br><br>
User defined functions can be registered using createFunc method of MathParser.
Example:<br>
  mathParser.createFunc("myfunc", new MyFunc());
*/
abstract public class OneParamFunc implements IFunction {
  public int getNumberOfParams() {
    return 1;
  }
  abstract public double run(IParameter[] p);
}
