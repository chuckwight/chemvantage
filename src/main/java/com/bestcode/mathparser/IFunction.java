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
IFunction interface represents a user defined callback function that takes n
parameters. During evaluation of an expression, MathParser calls<br><br>
IFunction.run(IParameter[] p)<br><br>
to request the value of the function when it takes p.length number of parameters.
<br><br>
For example, a user could define a function like this:
<br><br>
public class MyFunc implements IFunction {<br>
    &nbsp;&nbsp;  public double run(IParameter[] p){<br>
    &nbsp;&nbsp;&nbsp;&nbsp;     return do_something_with_params( p );<br>
    &nbsp;&nbsp;  }<br>
}<br>
<br><br>
Another user defined function "if" could be used as "if(1,3,4)". In this case,
implementation of IFunction.run(IParameter[]) could
return 3.0 if first parameter is >0 and it could return 4.0
if first parameter is 0 (false, to mimic "if" behavior).
<br><br>

User defined functions can be registered using createFunc method of MathParser.<br>
Example:<br>
  &nbsp;&nbsp; mathParser.createFunc("myfunc", new MyFunc());
*/
public interface IFunction {
  public double run(IParameter[] p);
  /**
    This method returns the length of the parameters array that will be
    passed to the run(IParameter[]) method.<br>
    For example, for a function like F(X, Y, Z), this function should return 3.
  */
  public int getNumberOfParams();
}
