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

import java.util.Locale;

/**
 * IMathParser interface provides access to an implementation of the math parser
 * algorithm. MathParserFactory.create() can be used to get a pointer to this
 * interface. An instance of IMathParser is not thread safe. Do not share the
 * same instance between threads. Create a separate instance for each thread or
 * do your own synchronization in the way your application logic requires.
 * <p>
 * You can create a parser using MathParserFactory.create(); This will return
 * you a IMathParser. You can set the expression to parse using
 * IMathParser.setExpression(String); And you can get the result using double
 * IMathParser.getValue();
 * </p>
 */
public interface IMathParser {
	/**
	 * Please see setExpression() method to read about Expression property.
	 */
	public String getExpression();

	/**
	 * Expression property represents the mathematical expression which is input
	 * to be evaluated by the user.
	 * 
	 * The expression can contain variables such as X, Y, T, HEIGHT, WEIGHT and
	 * so on. Expression can also contain functions that take one parameter, or
	 * two parameters. Variable and Function names are not case sensitive. <br>
	 * <br>
	 * When Expression is assigned a value, it becomes 'dirty' and further
	 * attempt to evaluate its value will require it to be parsed. But once it
	 * is parsed, and a parse tree representing the expression is formed, it
	 * won't be parsed again, until it is assignd a new string. Instead, the
	 * parse tree will be used to retrieve current results as the values of
	 * variables change. <br>
	 * <br>
	 * X, Y and PI variables are predefined and can be immediately used in the
	 * expression. CreateVar method can be used to add user variables. <br>
	 * <br>
	 * Predefined functions that take one parameter are: <br>
	 * <br>
	 * SQR: Square function which can be used as SQR(X) <br>
	 * <br>
	 * SIN: Sinus function which can be used as SIN(X), X is a real-type
	 * expression. Sin returns the sine of the angle X in radians. <br>
	 * <br>
	 * COS: Cosinus function which can be used as COS(X), X is a real-type
	 * expression. COS returns the cosine of the angle X in radians. <br>
	 * <br>
	 * ATAN: ArcTangent function which can be used as ATAN(X) <br>
	 * <br>
	 * SINH: Sinus Hyperbolic function which can be used as SINH(X) <br>
	 * <br>
	 * COSH: Cosinus Hyperbolic function which can be used as COSH(X) <br>
	 * <br>
	 * COTAN: which can be used as COTAN(X) <br>
	 * <br>
	 * TAN: which can be used as TAN(X) <br>
	 * <br>
	 * EXP: which can be used as EXP(X) <br>
	 * <br>
	 * LN: natural log, which can be used as LN(X) <br>
	 * <br>
	 * LOG: 10 based log, which can be used as LOG(X) <br>
	 * <br>
	 * SQRT: which can be used as SQRT(X) <br>
	 * <br>
	 * ABS: absolute value, which can be used as ABS(X) <br>
	 * <br>
	 * SIGN: SIGN(X) returns -1 if X<0; +1 if X>0, 0 if X=0; it can be used as
	 * SQR(X) <br>
	 * <br>
	 * TRUNC: Discards the fractional part of a number. e.g. TRUNC(-3.2) is -3,
	 * TRUNC(3.2) is 3. <br>
	 * <br>
	 * CEIL: CEIL(-3.2) = -3, CEIL(3.2) = 4 <br>
	 * <br>
	 * FLOOR: FLOOR(-3.2) = -4, FLOOR(3.2) = 3 <br>
	 * <br>
	 * RANDOM: <br>
	 * RND(X) generates a random INTEGER number such that 0 <= Result < int(X).
	 * If X is negative, then result is int(X) < Result <= 0. <br>
	 * <br>
	 * RANDOM(X) generates a random floating point number such that 0 <= Result
	 * < X. If X is negative, then result is X < Result <= 0. <br>
	 * <br>
	 * Predefined functions that take two parameters are: <br>
	 * <br>
	 * INTPOW: The INTPOW function raises Base to an integral power. INTPOW(2,
	 * 3) = 8. Note that result of INTPOW(2, 3.4) = 8 as well. <br>
	 * <br>
	 * POW: The Power function raises Base to any power. For fractional
	 * exponents or exponents greater than MaxInt, Base must be greater than 0.
	 * <br>
	 * <br>
	 * LOGN: The LogN function returns the log base N of X. Example: LOGN(10,
	 * 100) = 2 <br>
	 * <br>
	 * MIN: MIN(2, 3) is 2. <br>
	 * <br>
	 * MAX: MAX(2, 3) is 3. <br>
	 * <br>
	 * MOD: MOD(x,y) function implements the Java % (modulus) operator. <br>
	 * <br>
	 * Predefined functions that take three parameters are: <br>
	 * <br>
	 * IF: The IF(b, case1, case2) function provides branching capability. If b
	 * is not 0, then it returns case1, else it returns case2. Behavior is
	 * similar to Java's: <b>return b ? case1 : case2;</b><br>
	 * If b==0 then case1 will not be evaluated, and vice versa. Example:
	 * IF(HEIGHT, 3/HEIGHT, 3) will make sure 3/HEIGHT does not cause division
	 * by zero. <br>
	 * <br>
	 * 
	 * User functions can be added using createFunc method. Functions and
	 * Variables can be deleted using DeleteVar, DeleteFunc, DeleteAllVars,
	 * DeleteAllFuncs methods. <br>
	 * <br>
	 * 
	 * Supported operators are +,-,*,/,^,% (integer division),
	 * =(equals),&(and),|(or),!(not), &lt;&gt;(not equals), &lt;=(less than or
	 * equals), &gt;=(greater than or equals)
	 */
	public void setExpression(String newVal);

	/**
	 * Value property (getValue() method) is an intuitive way of retrieving the
	 * value of the input expression. Value property is a read only property
	 * which is in fact just an alias for the Evaluate method.
	 */
	public double getValue() throws Exception;

	/**
	 * Variable property is a way to set and get variable values. Throws
	 * Exception if the variable does not exist. Variable name is not case
	 * sensitive.
	 */
	public double getVariable(String varName) throws Exception;

	/**
	 * Variable property is a way to set and get variable values. setVariable
	 * function creates the variable if the variable does not exist. Variable
	 * name is not case sensitive. Throws exception if the variable needs to be
	 * created and the name is not a valid variable name. createVar is just an
	 * alias for this method.
	 */
	public void setVariable(String varName, double newVal) throws Exception;

	/**
	 * X property represents the X variable used in the mathematical expression
	 * which was input to be evaluated. You can set the X variable to a numeric
	 * value and call the Parse method (or Value property) to retrieve the new
	 * result of the expression. X variable is created by default for the
	 * convenience of the user. Additional variables can be added by using the
	 * CreateVar method. Variable names are case insensitive.
	 */
	public double getX() throws Exception;

	/**
	 * X property represents the X variable used in the mathematical expression
	 * which was input to be evaluated. You can set the X variable to a numeric
	 * value and call the Parse method (or Value property) to retrieve the new
	 * result of the expression. X variable is created by default for the
	 * convenience of the user. Additional variables can be added by using the
	 * CreateVar method. Variable names are case insensitive.
	 */
	public void setX(double newVal);

	/**
	 * Y property represents the Y variable used in the mathematical expression
	 * which was input to be evaluated. You can set the Y variable to a numeric
	 * value and call the Parse method (or Value property) to retrieve the new
	 * result of the expression. Y variable is created by default for the
	 * convenience of the user. Additional variables can be added by using the
	 * CreateVar method. Variable names are case insensitive.
	 */
	public double getY() throws Exception;

	/**
	 * Y property represents the Y variable used in the mathematical expression
	 * which was input to be evaluated. You can set the Y variable to a numeric
	 * value and call the Parse method (or Value property) to retrieve the new
	 * result of the expression. Y variable is created by default for the
	 * convenience of the user. Additional variables can be added by using the
	 * CreateVar method. Variable names are case insensitive.
	 */
	public void setY(double newVal);

	/**
	 * Set OptimizationOn to let the bcParser component evaluate constant
	 * expressions at parse time. The optimized parse tree will enhance
	 * subsequant evaluation operations, though initial parsing will be slower.
	 * <br>
	 * <br>
	 * Optimization is good if you are going to parse once and evaluate the same
	 * expression many many times with different variable values.
	 */
	public boolean getOptimizationOn();

	/**
	 * Set OptimizationOn to let the bcParser component evaluate constant
	 * expressions at parse time. The optimized parse tree will enhance
	 * subsequant evaluation operations, though initial parsing will be slower.
	 * <br>
	 * <br>
	 * Optimization is good if you are going to parse once and evaluate the same
	 * expression many many times with different variable values.
	 */
	public void setOptimizationOn(boolean newVal);

	/**
	 * @see IVariableResolver
	 * @return current IVariableResolver, null if not set before.
	 */
	public IVariableResolver getVariableResolver();

	/**
	 * Set the IVariableResolver for this parser instance. IVariableResolver is
	 * used to resolve variable values at evaluation time when they are not
	 * predefined.
	 * 
	 * @see IVariableResolver
	 * @param variableResolver
	 *            - user defined IVariableResolver to use to return the values
	 *            of undefined variables.
	 */
	public void setVariableResolver(IVariableResolver variableResolver);

	/**
	 * Evaluates the expression and returns the result of it. If it cannot be
	 * parsed or evaluated then this method throws Exception. <br>
	 * <br>
	 * Calling this method is identical to calling getValue()
	 */
	public double evaluate() throws Exception;

	/**
	 * Parses the expression and forms a parse tree. Throws Exception if it
	 * cannot parse. Upon successful completion of parsing, it will set the
	 * Dirty flag to false, so that unless the expression is changed or
	 * variables and functions added or removed, expression does not need to be
	 * re-parsed. Users may want to call the parse method directly to check the
	 * validity of an input expression using a try-except block. <br>
	 * <br>
	 * If OptimizationOn property is true, Parse method will optimize the parse
	 * tree by evaluating constant branches of the parse tree at that moment, so
	 * that Evaluate function will run faster.
	 */
	public void parse() throws Exception;

	/**
	 * Same as setVariable(String, double);
	 */
	public void createVar(String varName, double varValue) throws Exception;

	/**
	 * Creates a variable whose value is constant and cannot be changed. Using
	 * constants where possible enables the optimizer recognize constant
	 * branches and simplify them into single constant nodes to improve
	 * evaluation performance.
	 * 
	 * @param constName
	 * @param constValue
	 * @throws Exception
	 */
	public void createConstant(String constName, double constValue) throws Exception;

	/**
	 * createFunc method creates a new function that takes n number of
	 * parameters in the parser's list of functions. If the function name
	 * already exists, then createFunc throws Exception. Function name is not
	 * case sensitive. <br>
	 * <br>
	 * The second parameter is a reference to an implementation of the handler
	 * interface: <br>
	 * <br>
	 * interface IFunction {<br>
	 * &nbsp;&nbsp;&nbsp;&nbsp; public double run(IParameter[] parameters);<br>
	 * &nbsp;&nbsp;&nbsp;&nbsp; public int getNumberOfParams();<br>
	 * } <br>
	 * <br>
	 * While evaluation of the expression, when the registered function name is
	 * encountered, the user supplied IFunction.run(IParameter[]) will be
	 * called. <br>
	 * <br>
	 * This event handler ( IFunction.run(IParameter[]) ) will need to return a
	 * result (representing the value of the function) based on the parameters
	 * passed as an array of double values. <br>
	 * <br>
	 * The number of parameters that this newFuncName function will take is
	 * determined by the IFunction funcAddr parameter's
	 * IFunction.getNumberOfParams() method.
	 */
	public void createFunc(String newFuncName, IFunction funcAddr) throws Exception;

	/**
	 * createDefaultFuncs method creates some predefined functions in the
	 * parser's list of functions. <br>
	 * <br>
	 * Predefined functions that take one parameter are: <br>
	 * <br>
	 * SQR: Square function which can be used as SQR(X) <br>
	 * <br>
	 * SIN: Sinus function which can be used as SIN(X), X is a real-type
	 * expression. Sin returns the sine of the angle X in radians. <br>
	 * <br>
	 * COS: Cosinus function which can be used as COS(X), X is a real-type
	 * expression. COS returns the cosine of the angle X in radians. <br>
	 * <br>
	 * ATAN: ArcTangent function which can be used as ATAN(X) <br>
	 * <br>
	 * SINH: Sinus Hyperbolic function which can be used as SINH(X) <br>
	 * <br>
	 * COSH: Cosinus Hyperbolic function which can be used as COSH(X) <br>
	 * <br>
	 * COTAN: which can be used as COTAN(X) <br>
	 * <br>
	 * TAN: which can be used as TAN(X) <br>
	 * <br>
	 * EXP: which can be used as EXP(X) <br>
	 * <br>
	 * LN: natural log, which can be used as LN(X) <br>
	 * <br>
	 * LOG: 10 based log, which can be used as LOG(X) <br>
	 * <br>
	 * SQRT: which can be used as SQRT(X) <br>
	 * <br>
	 * ABS: absolute value, which can be used as ABS(X) <br>
	 * <br>
	 * SIGN: SIGN(X) returns -1 if X<0; +1 if X>0, 0 if X=0; it can be used as
	 * SQR(X) <br>
	 * <br>
	 * TRUNC: Discards the fractional part of a number. e.g. TRUNC(-3.2) is -3,
	 * TRUNC(3.2) is 3. <br>
	 * <br>
	 * CEIL: CEIL(-3.2) = 3, CEIL(3.2) = 4 <br>
	 * <br>
	 * FLOOR: FLOOR(-3.2) = -4, FLOOR(3.2) = 3 <br>
	 * <br>
	 * RANDOM: <br>
	 * RND(X) generates a random INTEGER number such that 0 <= Result < int(X).
	 * <br>
	 * <br>
	 * RANDOM(X) generates a random floating point number such that 0 <= Result
	 * < X. <br>
	 * <br>
	 * Predefined functions that take two parameters are: <br>
	 * <br>
	 * INTPOW: The INTPOW function raises Base to an integral power. INTPOW(2,
	 * 3) = 8. Note that result of INTPOW(2, 3.4) = 8 as well. <br>
	 * <br>
	 * POW: The Power function raises Base to any power. For fractional
	 * exponents or exponents greater than MaxInt, Base must be greater than 0.
	 * <br>
	 * <br>
	 * LOGN: The LogN function returns the log base N of X. Example: LOGN(10,
	 * 100) = 2 <br>
	 * <br>
	 * MIN: MIN(2, 3) is 2. <br>
	 * <br>
	 * MAX: MAX(2, 3) is 3. <br>
	 * <br>
	 * MOD: MOD(x,y) function implements the Java % (modulus) operator. <br>
	 * <br>
	 * Predefined functions that take three parameters are: <br>
	 * <br>
	 * IF: The IF(b, case1, case2) function provides branching capability. If b
	 * is not 0, then it returns case1, else it returns case2. Behavior is
	 * similar to Java's: <b>return b ? case1 : case2;</b><br>
	 * If b==0 then case1 will not be evaluated, and vice versa. Example:
	 * IF(HEIGHT, 3/HEIGHT, 3) will make sure 3/HEIGHT does not cause division
	 * by zero. <br>
	 * <br>
	 */
	public void createDefaultFuncs() throws Exception;

	/**
	 * X, Y and PI variables are predefined and can be immediately used in the
	 * expression. Initial values of X and Y are 0. PI is 3.14159265358979
	 */
	public void createDefaultVars();

	/**
	 * deleteVar method deletes an existing variable from the list of available
	 * variables. If the variable does not exist, then deleteVar does nothing.
	 * <br>
	 * <br>
	 * When a variable is deleted Dirty flag is set to true so that next time
	 * the Evaluate function is called the expression will be reparsed. Variable
	 * name is not case sensitive.
	 */
	public void deleteVar(String varName);

	/**
	 * deleteFunc method deletes an existing function from the list of available
	 * functions. If the function does not exist, deleteFunc does nothing. <br>
	 * <br>
	 * When a function is deleted Dirty flag is set to true so that next time
	 * the Evaluate function is called the expression will be reparsed. Function
	 * name is not case sensitive.
	 */
	public void deleteFunc(String funcName);

	/**
	 * DeleteAllVars method deletes all variables from the list of available
	 * variables. <br>
	 * <br>
	 * This action may be useful when number of unused variables is too high
	 * that causes performance to degrade. <br>
	 * <br>
	 * When a variable is deleted Dirty flag is set to true so that next time
	 * the Evaluate function is called the expression will be reparsed.
	 */
	public void deleteAllVars();

	/**
	 * DeleteAllFuncs method deletes all variables from the list of available
	 * functions. <br>
	 * <br>
	 * This action may be useful when number of unused functions is too high
	 * that causes performance to degrade. <br>
	 * <br>
	 * When a function is deleted Dirty flag is set to true so that next time
	 * the Evaluate function is called the expression will be reparsed.
	 */
	public void deleteAllFuncs();

	/**
	 * Returns the list of variables as an array of strings. Each element of the
	 * array is guaranteed not to be null.
	 */
	public String[] getVariables();

	/**
	 * Returns an array of functions declared for this parser. Elements of the
	 * array are guaranteed not to be null.
	 */
	public String[] getFunctions();

	/**
	 * FreeParseTree can be explicitly called to free the resources taken by the
	 * allocated Parse tree when an expression is parsed. FreeParseTree sets the
	 * Dirty flag to true so that next time the Evaluate function is called,
	 * expression will be parsed forming a new, valid parse tree to be
	 * evaluated.
	 */
	public void freeParseTree();

	/**
	 * Returns true if a variable with the name 'varName' is used in the current
	 * expression. Variable name is not case sensitive. Throws exception if
	 * expression is not parsed and cannot be parsed.
	 */
	public boolean isVariableUsed(String varName) throws Exception;

	/**
	 * Returns true if a function with the name 'funcName' is used in the
	 * current expression. Function name is not case sensitive. Throws exception
	 * if expression is not parsed and cannot be parsed.
	 */
	public boolean isFuncUsed(String funcName) throws Exception;

	/**
	 * Returns the list of variables used in the current expression as an array
	 * of Strings. Each element of the array is guaranteed not to be null. If
	 * variables are not defined ahead of time, then VariableResolver must have
	 * been set. Otherwise, when an undefined variable is found in the
	 * expression, ParserException will be thrown.
	 * 
	 * @return Array of variable names that are currently defined for this
	 *         parser instance.
	 */
	public String[] getVariablesUsed() throws Exception;

	/**
	 * Returns true if a variable with the name 'varName' is present in the
	 * current variables list as a variable or constant.
	 */
	public boolean isVariable(String varName);

	/**
	 * Returns true if a constant with the name 'constName' is defined. If the
	 * given name is defined as a variable, but not a constant, this method
	 * returns false.
	 */
	public boolean isConstant(String constName);

	/**
	 * Returns true if a function with the name 'funcName' is present in the
	 * current functions list.
	 */
	public boolean isFunction(String funcName);

	/**
	 * Optimizes the parse tree by finding branches that evaluate to a constant
	 * and replacing them with a leaf representing the constant. Until the
	 * expression is changed and reparsed, further evaluation requests will be
	 * quicker. <br>
	 * <br>
	 * If the same expression will not be evaluated repeatedly with varying
	 * values of parameters used in it, then optimization will not bring any
	 * gain, but will slow performance. <br>
	 * <br>
	 * If OptimizationOn property is set to true, this method is called
	 * automatically when an evaluation is requested by calling Evaluate method
	 * or getValue() method.
	 */
	public void optimize();

	/**
	 * Sets the locale for the parser to use while constructing messages and
	 * decising decimal separator if {@link isLocaleSpecificDecimals} is true.
	 * Parser holds a static pool of translated messages mapped for each locale.
	 * When this method is called, it first checks to see if the resource bundle
	 * is already loaded in this pool. If it is loaded, it re-uses that pool. If
	 * it was not loaded before, it loads it, adds it to the pool and sets the
	 * specific bundle for this instance of the parser to use. This mechanism
	 * allows the parser instances in the same VM efficiently use different
	 * locales independent of each other. For example, if you instantiate 100
	 * parsers to use US English locale and then another 200 to use Chineese,
	 * then the parser will load the two resource bundles for once each and
	 * related instances will share the same ones.<br>
	 * <br>
	 * When you set a locale, let's say "de_DE", you need to have a matching
	 * property file to be loaded by the parser,
	 * "com/bestcode/mathparser/mathparser_de_DE.properties". If such file is
	 * not found, the default version
	 * ("com/bestcode/mathparser/mathparser.properties"), which contains English
	 * messages will be loaded. The file IO is done once for each locale, not
	 * for each instance of the parser.<br>
	 * <br>
	 * Messages may contain parameter placeholders. For example: "Variable {0}
	 * does not exist". You need to have your own messages translated
	 * accordingly.
	 */
	public void setLocale(Locale l);

	/**
	 * Tells if expressions use locale specific decimal separator such as dot or
	 * comma. For example, if true, in the US expressions would use dot (.) as a
	 * decimal separator, and in the Europe they would use comma (,) for decimal
	 * separator. If this property is true, and if a Locale set by
	 * {@link setLocale} dictates that comma is going to be decimal separator,
	 * then function parameter separator is going to be colon (:). If decimal
	 * separator is dot (.), then function parameter is comma (,)
	 * 
	 * @return boolean
	 */
	public boolean isLocaleSpecificDecimals();

	/**
	 * Tells the parser to support locale specific decimal separator such as
	 * dot(.) and comma(,) depending on the locale set by setLocale.
	 * 
	 * @param useLocaleSpecificDecimals
	 * @see isLocaleSpecificDecimals
	 */
	public void setLocaleSpecificDecimals(boolean useLocaleSpecificDecimals);

	/**
	 * Returns the locale of this parser instance.
	 */
	public Locale getLocale();

	/**
	 * @return the decimal separator character decided by
	 *         {@link isLocaleSpecificDecimals} and {@link getLocale}.
	 */
	public char getDecimalSeparator();

	/**
	 * @return the character used to separate function parameters. If
	 *         {@link getDecimalSeparator} is dot (.) then the character for
	 *         function parameter separator is comma (,). If comma is used for
	 *         decimal separator, then function parameter separator is colon
	 *         (:).
	 */
	public char getFunctionParamSeparator();
}
