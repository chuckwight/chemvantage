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
 * ParserException is thrown by some methods of IMathParser interface if an 
 * expression cannot be parsed. These methods are:
 * parse(), evaluate(), getValue()
 */
public class ParserException extends Exception {
	private static final long serialVersionUID = 7971694971047377967L;

	String m_err;
  String m_exp;
  /**
   * Non-public constructor.
   */
  ParserException(String msg, String errPart, String expression) {
    super(msg);
    m_err = errPart;
    m_exp = expression;
  }
  /**
   * Returns the expression string that cannot be parsed.
   */
  public String getInvalidPortionOfExpression(){
    return m_err;
  }
  /**
   * Returns the subexpression that is the immediate parent of the error portion.<br>
   * For example, if the expression is 3+LN(MAX(4,))
   * then getInvalidPortionOfExpression() would return MAX(4,) and getSubExpression()
   * would return LN(MAX(4,))<br><br>
   * The returned sub expressions may not exactly match the original string supplied 
   * as the expression since space characters and paranthesis may be substituted for efficient
   * parsing.
   */
  public String getSubExpression(){
    return m_exp;
  }
}