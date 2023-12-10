/**********************************************************************
*
*  PRODUCT: Bestcode Math Parser
*  COPYRIGHT:  (C) COPYRIGHT Suavi Ali Demir 2000-2016
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
*  has been deposited with the U.S. Copyright Office, belongs
*  to Suavi Ali Demir.
* 
* 01/23/2016. version 4.3
*               Support locale specific decimal separators as dot (.) and comma (,).
*               min and max functions can now take more than two parameters.
* 
* 02/14/2015. version 4.2
*               Optimize for Java 1.8. Workaround for slow String.substring problem that
*               is introduced by Java 1.8. After various performance improvements parsing
*               is now 5 times faster in 1.8 and 3 times faster in 1.5.
* 
* 12/26/2014. version 4.1
*               Move minimum support JDK level from 1.4 to 1.5. Use Generics internally.
*               Use ConcurrentHashMap instead of Hashtable for Variable and Function map. 
* 
* 10/05/2011. version 4.0
* 							toUpperCase causes Turkish i to become I with dot which is outside
* 							the valid range of math parser characters. To fix it, we use US locale
* 							regardless of the default locale when we uppercase.
* 
* 09/28/2011. version 3.9
* 							Support for getVariablesUsed() function.
* 
* 07/02/2010.  version 3.8 - 
* 							lastWasOperator was not marked in FindLastOper function for case E:
* 
* 08/07/2009.  version 3.7 - IVariableResolver provides values for undefined variables.
* 												@see IMathParser.setVariableResolver
* 
* 05/15/2009. version 3.6 functions that take no parameters such as f( ) 
* 
* 01/17/2009. version 3.5 New function: isConstant tells whether given constant is defined or not.
* 
* 08/25/2008. version 3.4 Bug Fix: 0*E-1*P has problem with E. Thinks scientific notation due to E. 
* 
* 10/31/2007. version 3.3 Bug Fix: ((  (x)))-2 fails.
* 
* 10/21/2007. version 3.2 Bug Fix: 100.00 + -10.00 does not like the space between + -. 
* 
* 12/25/2005. version 3.1 Bug Fix: Anomaly introduced in previous fix where an expression with paranthesis
* like ( -(x+1)) is being rejected as invalid and requires (-(x+1)). 
* The leading space before - sign needs to be handled. This happens
* if the expression portion is in paranthesis only.
*    
* 12/04/2005. version 3.0 Expression "abc" where "abc" is not yet defined as a var should throw
* ParserException instead of just Exception so that user can get invalid portion of expression.
* An expression like "x+si n(y)" should not be converted to "x+sin(y)" and become valid. 
* It should throw exception that "si n(y)" is invalid in "x+si n(y)". As a side effect, now, 
* invalid portion of an expression (when reported by the ParserException) can now contain white space
* at start and end. For example: "x+ abc  " will cause " abc  " to be reported as invalid portion. If you
* define user variables on the fly based on this reporting, make sure to trim white space.     
*
* 11/09/2005. version 2.9 Support for functions that can take any number of parameters: f(...)
* Cleanup and consolidation of complicated Interfaces and implementations using the new IFunction, IParameter interfaces.
*
* 10/12/2003. version 2.8 Support for constants such as PI, C etc.
* This makes it possible to optimize constant branches of the parse tree
* to a single constant value node.
*
* 03/18/2003. version 2.7 Support for operators with 2 chars: &lt;&gt;, &gt;=,
* &lt;= (<>, <=, >=)
*
* 03/06/2003. version 2.6 Deprecates createNParamFunc(String, IFunction, int)
* 						in favor of createNParamFunc(String, IFunction)
*
* 07/19/2002. version 2.5 Adds support for logical operators: <>=|&
* 
* 06/02/2002. version 2.4 Adds support for better error information using ParserException.
* 
* 03/24/2002. version 2.3 Renamed all classes so that versions 2.x can co exist
* with versions 1.x on the same class path.
* 
* 03/22/2002. version 2.2 Fixes 1+-+2 bug.
* 
* 03/14/2002. version 2.1 Adds support for:
*   1. Unary operators next to binary operators: "2+-3" = -1 where unary operator has precedence.
*   2. Scientific notation as "1+3E+2" = 3001 and "1+3E-2" = 1.03
*   3. Better correction for <thinks "020" is a valid double> bug.
* 
* 02/28/2002. version 2.0 Adds support for functions that take n number of params.
* 12/05/2001. version 1.2 Shortens some class file names to help mac compatibility.
* 10/01/2001. version 1.1 Fixes getVariable(String) method.
*
***********************************************************************/
package com.bestcode.mathparser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.Collator;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
//trial version support:
//import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.bestcode.util.QuickSort;
import com.bestcode.util.StrUtil;

class MathParserImpl implements IMathParser {

  private String m_Expression;
  private boolean m_Dirty;
  private boolean m_OptimizationOn;
  private boolean m_LocaleSpecificDecimals;
  private char m_DecimalSeparator;
  private char m_FunctionParamSeparator;
  private DecimalFormat m_DecimalFormat;
  private Node m_Node;
  private ConcurrentHashMap<String, Variable> m_Variables;
  private ConcurrentHashMap<String, IFunction> m_Functions;

  //When set, this is used to resolve the values of undefined variables.
  IVariableResolver m_VariableResolver;
  
  //localization:
  private Locale m_Locale = Locale.getDefault();
  private static ConcurrentHashMap<Locale, StrUtil> translators =
  		new ConcurrentHashMap<Locale, StrUtil>(); //Resource Bundle users hashed by Locale's.
  private StrUtil m_Translate;

  ////////////////////////////////////////////////////////////////////////////////
  //Functions that the parser accepts and uses. These are just predicates 
  //(pointers to functions) and they do not store state information so 
  //they can be shared by multiple instances of the math parser 
  //effectively reducing object instantiations needed per parser instance.
  static final  __sum   		sum_    		= new __sum();
  static final  __greater   greater_    = new __greater();
  static final  __less      less_       = new __less();
  static final  __notequals notequals_  = new __notequals();
  static final  __ltequals  ltequals_   = new __ltequals();
  static final  __gtequals  gtequals_   = new __gtequals();
  
  static final  __equals    equals_     = new __equals();
  static final  __and       and_        = new __and();
  static final  __or        or_         = new __or();
  static final  __not       not_        = new __not();
  
  static final  __unaryadd  unaryadd_   = new __unaryadd();
  static final  __add       add_        = new __add();
  static final  __subtract  subtract_   = new __subtract();
  static final  __multiply  multiply_   = new __multiply();
  static final  __divide    divide_     = new __divide();
  static final  __modulo    modulo_     = new __modulo();
  static final  __intdiv    intdiv_     = new __intdiv();
  static final  __negate    negate_     = new __negate();
  static final  __intpower  intpower_   = new __intpower();
  static final  __square    square_     = new __square();
  static final  __power     power_      = new __power();
  static final  __sin       sin_        = new __sin();
  static final  __cos       cos_        = new __cos();
  static final  __arctan    arctan_     = new __arctan();
  static final  __sinh      sinh_       = new __sinh();
  static final  __cosh      cosh_       = new __cosh();
  static final  __cotan     cotan_      = new __cotan();
  static final  __tan       tan_        = new __tan();
  static final  __exp       exp_        = new __exp();
  static final  __ln        ln_         = new __ln();
  static final  __log10     log10_      = new __log10();
  static final  __log2      log2_       = new __log2();
  static final  __logN      logN_       = new __logN();
  static final  __sqrt      sqrt_       = new __sqrt();
  static final  __abs       abs_        = new __abs();
  static final  __min       min_        = new __min();
  static final  __max       max_        = new __max();
  static final  __sign      sign_       = new __sign();
  static final  __trunc     trunc_      = new __trunc();
  static final  __ceil      ceil_       = new __ceil();
  static final  __floor     floor_      = new __floor();
  static final  __rnd       rnd_        = new __rnd();
  static final  __random    random_     = new __random();
  static final  __if        if_         = new __if();
  
  MathParserImpl() {
    //init Localization support:
    m_Translate = translators.get(m_Locale);
    if (m_Translate==null){
      m_Translate = new StrUtil(loadBundle());
      if (m_Translate!=null){
        translators.put(m_Locale, m_Translate);
      }
    }

    m_Expression = "";
    m_Node = null;
    m_Dirty = true; //means it is not parsed yet.
    m_OptimizationOn = false;
    m_DecimalSeparator = '.';
    m_FunctionParamSeparator = ',';

    m_Variables = new ConcurrentHashMap<String, Variable>();
    m_Functions = new ConcurrentHashMap<String, IFunction>();
    try {
      createDefaultFuncs();
      createDefaultVars();
    }
    catch(Throwable t){
      //terrible, whats wrong? don't let user go on if this happens.
      //possible that they change the code and make a mistake.
      System.out.println(t.getMessage());
      t.printStackTrace();
      throw new RuntimeException(t.getMessage());
    }
  }

  /**
   * Attempts to load the resource bundle for math parser.
   * If it fails, it creates a dummy resource bundle.
   */
  private ResourceBundle loadBundle(){
    ResourceBundle rb=null;
    try{
      // Obfuscation friendly package name:
      rb = ResourceBundle.getBundle(this.getClass().getPackage().getName()+".mathparser", m_Locale, this.getClass().getClassLoader());
    }
    catch(Exception ex){
    }
    if(rb==null){
      InputStream stream = new ByteArrayInputStream(new byte[0]);
      try{
        rb = new PropertyResourceBundle(stream);
      }catch(Exception ex){};
    }
    return rb;
  }

  public Locale getLocale(){
    return m_Locale;
  }
  
  public void setLocale(Locale l){
    if (l==null){
      throw new IllegalArgumentException("Locale==null");
    }
    m_Locale = l;
    resetDecimalSeparator();
    
    Object t = translators.get(l);
    if (t!=null){ //if already loaded, use existing...
      m_Translate = (StrUtil)t;
    }
    else {
      m_Translate = new StrUtil(loadBundle());
      if (m_Translate!=null){
        translators.put(l, m_Translate); //save for future re-use.
      }
    }
  }

  public void setLocaleSpecificDecimals(boolean useLocaleSpecificDecimals) {
  	m_LocaleSpecificDecimals = useLocaleSpecificDecimals;
  	resetDecimalSeparator();
  }

	private void resetDecimalSeparator() {
		if (m_LocaleSpecificDecimals) {
  		if (m_Locale == null) {
  			m_DecimalSeparator = '.';
  			m_FunctionParamSeparator = ',';
  			m_DecimalFormat = null;
  			return;
  		}
			m_DecimalFormat = (DecimalFormat) DecimalFormat.getInstance(m_Locale);
  		DecimalFormatSymbols symbols = m_DecimalFormat.getDecimalFormatSymbols();
  		m_DecimalSeparator = symbols.getDecimalSeparator();
  		if (m_DecimalSeparator == ',') {
  			m_FunctionParamSeparator = ':';
  		} else {
  			m_FunctionParamSeparator = ',';
  		}
  	} else {
  		m_DecimalSeparator = '.';
  		m_FunctionParamSeparator = ',';
  	}
	}

  public boolean isLocaleSpecificDecimals() {
  	return m_LocaleSpecificDecimals;
  }
  
  public String getExpression() {
    return m_Expression;
  }

  public void setExpression(String newVal) {
    m_Expression = newVal;
    m_Dirty = true;
  }

  public double getValue() throws Exception, ParserException {
    return evaluate();
  }

  public double getVariable(String varName) throws Exception {
    if (varName==null){
      throw new IllegalArgumentException("Variable name cannot be null.");
    }
    String upcName = varName.toUpperCase(Locale.US);
    Variable aVariable = (Variable)m_Variables.get(upcName);
    if (aVariable==null){
      throw new Exception(m_Translate.getMessage("VarNtExst", varName));
    }
    return aVariable.value;
  }

  public void setVariable(String varName, double newVal) throws Exception {
    if (varName==null){
      throw new IllegalArgumentException("Variable name cannot be null.");
    }
    String upcName = varName.toUpperCase(Locale.US);
    //check to see if the variable already exists:
    Variable existing = (Variable)m_Variables.get(upcName);
    if (existing!=null){
      if(existing.isConstant){
        throw new Exception(m_Translate.getMessage("CnstVal", varName));
      }
      existing.value = newVal;
    }
    else {
      if (!isValidName(upcName)){
        throw new Exception(m_Translate.getMessage("NtVarNm", varName));
      }
      //create the variable:
      m_Variables.put(upcName, new Variable(newVal));
      m_Dirty = true;
    }
  }

  public double getX() throws Exception {
    return getVariable("X");
  }

  public void setX(double newVal) {
    try{
      setVariable("X", newVal);
    }catch(Exception ex){}
  }

  public double getY() throws Exception {
    return getVariable("Y");
  }

  public void setY(double newVal) {
    try{
      setVariable("Y", newVal);
    }catch(Exception ex){};
  }

  public boolean getOptimizationOn() {
    return m_OptimizationOn;
  }

  public void setOptimizationOn(boolean newVal) {
    m_OptimizationOn = newVal;
  }
  public double evaluate() throws Exception, ParserException {
    if (m_Dirty) { //if the expression has been changed, we need to parse it again
      parse();
    }
    return m_Node.getValue(); //this will start the chain reaction to get the
                //value of all nodes
  }

  public void parse() throws Exception, ParserException{
    //trial version code:
    /*
    Date date = new Date();
    Date ends = new Date("01/15/2017");
    if (date.after(ends)) {
      throw new Exception("Trial period has ended.");
    }
    */
    if (m_Expression==null || ! (m_Expression.length() > 0)){
      m_Node = null;
      throw new Exception(m_Translate.getMessage("ExpEmpty"));
    }
    //we will check for uppercase version of function defs
    char temp[] = m_Expression.toUpperCase(Locale.US).toCharArray(); //StrUtil.replace(m_Expression, ' ').toUpperCase(Locale.US).toCharArray(); //not to change the original string, make a copy of it and convert to uppercase

    int i;
    int len = temp.length;
    int j = len-1;

    len /= 2; //we will scan starting from both ends:
    for (i = 0; !(i > len); i++) {
      if ((temp[i] == '[') || (temp[i] == '{')) {  //scanning half way from start
         temp[i] = '(';
      }
      else if ((temp[i] == ']') || (temp[i] == '}')) {
        temp[i] = ')';
      }

      if ((temp[j] == '[') || (temp[j] == '{')) { //scanning other half from end
         temp[j] = '(';
      }
      else if ((temp[j] == ']') || (temp[j] == '}')) {
        temp[j] = ')';
      }
      --j;
    }

    m_Node = null; //should assign NULL to make sure we don't free when object doesn't exist.
    //call the recursive parsing function to generate the node structure tree
    FastStr formula = new FastStr(temp, 0, temp.length);
    
  	int brackets = checkBrackets(formula);
    if (brackets>-1 && brackets<formula.length()){
			throw new ParserException(m_Translate.getMessage("BrcktMis", formula.toString(),
					String.valueOf(brackets)), formula.substring(brackets, formula.length()).toString(),
					formula.toString());
    }
    else
  	if(brackets==formula.length()){
    	throw new ParserException(m_Translate.getMessage("MisBrckt", formula.toString()), formula.toString(), formula.toString());
  	}
    
    if ((m_Node = createParseTree(formula)) == null){
    	throw new ParserException(m_Translate.getMessage("ExpNtVld", formula.toString(), formula.toString()), formula.toString(), formula.toString());
    }

    if (m_OptimizationOn){
      optimize(); //will make sure m_Node tree is lean and mean
    }
    m_Dirty = false; //note that we parsed it once. Unless the expression is changed we do not need to reparse it.
  }

  public void createVar(String varName, double varValue) throws Exception {
    setVariable(varName, varValue);
  }

  public void createConstant(String varName, double newVal) throws Exception {
    if (varName==null){
      throw new IllegalArgumentException("Constant name cannot be null.");
    }
    String upcName = varName.toUpperCase(Locale.US);
    //check to see if the variable already exists:
    Variable existing = (Variable)m_Variables.get(upcName);
    if (existing!=null){
      //complain that variable already exists.
      throw new Exception(m_Translate.getMessage("VarExt", varName));
    }
    else {
      if (!isValidName(upcName)){
        throw new Exception(m_Translate.getMessage("NtVarNm", varName));
      }
      //create the variable:
      Variable var = new Variable(newVal);
      var.isConstant = true;
      m_Variables.put(upcName, var);
      m_Dirty = true;
    }
  }

  public void createFunc(String newFuncName, IFunction funcAddr)
  throws Exception {
    if (newFuncName==null){ throw new IllegalArgumentException("Function name cannot be null."); }
    if (funcAddr==null){ throw new IllegalArgumentException("Function implementation cannot be null."); }

    String upcName = newFuncName.toUpperCase(Locale.US);
    if (!isValidName(upcName)) { //must contain uppercase letters only
      throw new Exception(m_Translate.getMessage("NtFncNm", newFuncName));
    }

    if (isFunction(upcName)) {
      throw new Exception(m_Translate.getMessage("FncExst", newFuncName));
    }
    //if newFuncName doesn't exist it is inserted:
    m_Functions.put(upcName, funcAddr);
    m_Dirty= true; //previously bad expression may now be ok, we should reparse it
  }

  public void createDefaultFuncs() throws Exception {
    createFunc("SQR", square_);
    createFunc("SIN", sin_);
    createFunc("COS", cos_);
    createFunc("ATAN", arctan_);
    createFunc("SINH", sinh_);
    createFunc("COSH", cosh_);
    createFunc("COTAN", cotan_);
    createFunc("TAN", tan_);
    createFunc("EXP", exp_);
    createFunc("LN", ln_);
    createFunc("LOG", log10_);
    createFunc("SQRT", sqrt_);
    createFunc("ABS", abs_);
    createFunc("SIGN", sign_);
    createFunc("TRUNC", trunc_);
    createFunc("CEIL", ceil_);
    createFunc("FLOOR", floor_);
    createFunc("RND", rnd_);
    createFunc("RANDOM", random_);

    createFunc("INTPOW", intpower_);
    createFunc("POW", power_);
    createFunc("LOGN", logN_);
    createFunc("MIN", min_);
    createFunc("MAX", max_);
    createFunc("MOD", modulo_);

    createFunc("IF", if_);
    createFunc("SUM", sum_);
  }

  public void createDefaultVars() {
    try{
      createConstant("PI", 3.14159265358979);
      createVar("X", 0.0);
      createVar("Y", 0.0);
    }catch(Exception ex){
      //we know that these are valid names, so there won't be an exception.
    }
  }

  public void deleteVar(String varName) {
    if (varName==null){
      throw new IllegalArgumentException("Variable name cannot be null.");
    }
    //this function deletes the variable only if it finds it.
    String upcName = varName.toUpperCase(Locale.US);
    m_Variables.remove(upcName);
    m_Dirty = true;
  }

  public void deleteFunc(String funcName){
    if (funcName==null){
      throw new IllegalArgumentException("Function name cannot be null.");
    }
    String upcName = funcName.toUpperCase(Locale.US);
    m_Functions.remove(upcName);
    m_Dirty = true;
  }

  public void deleteAllVars() {
    m_Variables.clear();
    m_Dirty = true;
  }

  public void deleteAllFuncs() {
    m_Functions.clear();
    m_Dirty = true;
  }

  public void freeParseTree() {
    //delete m_Node;
    m_Node = null; //this should free it all
    m_Dirty = true; //so that next time we call Evaluate, it will call the Parse method.
  }
  
	/** 
	 * Returns the list of variables used in the current expression as an array of Strings.
	 * Each element of the array is guaranteed not to be null.
	 * If variables are not defined ahead of time, then VariableResolver must have been set. 
	 * Otherwise, when an undefined variable is found in the expression, ParserException will be thrown.
	 * @return Array of variable names that are currently defined for this parser instance.
	 */ 
	public String[] getVariablesUsed() throws Exception
	{   
		if (m_Dirty && m_Expression!=null && m_Expression.length()>0)
		{
			this.parse();
		}
		ArrayList<String> list = new ArrayList<String>();
		String[] a = getVariables();
		for(int i=0; i<a.length; i++)
		{
			if(isVariableUsed(a[i]))
			{
				list.add(a[i]);
			}
		}
		findVariablesUsed(m_Node, list);
		return (String[])list.toArray(new String[list.size()]);
	}
	
	static void findVariablesUsed(Node aNode, ArrayList<String> varList)
	{
		if (aNode instanceof UnknownVarNode) 
		{
			varList.add(((UnknownVarNode)aNode).m_VarName);
		}
		else if (aNode instanceof NParamNode) 
		{
			NParamNode nParamNode = (NParamNode)aNode;
			Node[] nodes = nParamNode.nodes;

			for (int i=0; i<nodes.length; i++)
			{
          if ((nodes[i] instanceof UnknownVarNode))
          {
              varList.add(((UnknownVarNode)nodes[i]).m_VarName);
          }
          else
          {
              findVariablesUsed(nodes[i], varList);
          }
			}
			//if all parameters of the function are constants (basic nodes):
		}
	}
  

  public boolean isVariableUsed(String varName) throws Exception
  {
    if (varName==null){  throw new IllegalArgumentException("Variable name cannot be null."); }

    if(m_Dirty){
      parse(); //to create parse tree if it is not created yet.
    }
    
    varName = varName.toUpperCase(Locale.US);
    Object aVar = m_Variables.get(varName);
    if (aVar!=null) {
      //we need to check if it is not null, because there might be some null pointers in the tree
      return m_Node.isUsed(aVar);
    }else{
      return m_Node.isUsed(varName); //to support UnknownVarNode
    }
  }

  public boolean isFuncUsed(String funcName) throws Exception {
    if (m_Dirty){
      parse(); //to create parse tree if it is not created yet.
    }
    funcName = funcName.toUpperCase(Locale.US);
    IFunction aFunc = (IFunction)m_Functions.get(funcName);
    if (aFunc != null){
      //we need to check if it is not null, because there might be some null pointers in the tree
      return m_Node.isUsed(aFunc);
    }
    return false;
  }

  public boolean isVariable(String varName) {
    if (varName==null){ throw new IllegalArgumentException("Variable name cannot be null."); }
    varName = varName.toUpperCase(Locale.US);
    return m_Variables.containsKey(varName);
  }
  
  public boolean isConstant(String constName) {
    if (constName==null){ throw new IllegalArgumentException("Constant name cannot be null."); }
    constName = constName.toUpperCase(Locale.US);
    Variable var = (Variable)m_Variables.get(constName);
    if(var==null){
    	return false;
    }
    return var.isConstant;
  }

  public boolean isFunction(String funcName) {
    if (funcName==null){ throw new IllegalArgumentException("Function name cannot be null."); }
    funcName = funcName.toUpperCase(Locale.US);
    return m_Functions.containsKey(funcName);
  }

  public String[] getVariables(){
    String[] a = new String[m_Variables.size()];
    int i=0;
    Enumeration<String> varkeys = m_Variables.keys();
    while(varkeys.hasMoreElements()){
      a[i++] = varkeys.nextElement();
    }
    return new Sort().sort(a);
  }

  public String[] getFunctions(){
    int size = m_Functions.size();
    String[] a = new String[size];
    int i=0;
    Enumeration<String> varkeys = m_Functions.keys();
    while(varkeys.hasMoreElements()){
      a[i++] = varkeys.nextElement();
    }
    return new Sort().sort(a);
  }

  //------------------------------------------------------------------------------
  //END OF PUBLIC METHODS.
  //------------------------------------------------------------------------------
  
  /**
   * Valid char definition for function and variable names.
   */
  private boolean isValidChar(int index, char c){
    if (index==0){
      if ( c>='A' && c<='Z' ){
        return true;
      }
      if (c=='_'){  return true; }
      return false;
    }
    if ( (c>='0' && c<='9') || (c>='A' && c<='Z') ){
      return true;
    }
    if (c=='_'){ return true; }
    return false;    
  }
  /**
   * Valid name definition for function and variable names.
   */
  private boolean isValidName(String name){
    int len = name.length();
    for (int i=0; i<len; i++){
      if(!isValidChar(i, name.charAt(i))){
        return false;
      }
    }
    return true;
  }
  //------------------------------------------------------------------------------
  /**
    Makes sure number of opening brackets "(" are equal to
    the number of closing brackets ")" and they are consistent.
    If there is more ) than (, then it returns the character index of
    the ).
    If there is more ( than ), then it returns formula.length(), (the index at which an extra ")" would be expected.)
    If all is fine, it returns -1.
  */
  protected static int checkBrackets(FastStr formula) {
       //this function checks to see if the order and double of brackets are correct
       //it will say ok if it sees something like 3+()()
       int i, n=0, len = formula.length();
       char ch;
       for (i = 0; i<len; i++) { //if length<1 loop won't execute
            ch = formula.charAt(i);
            if (ch == '(')
                  ++n;
            else if (ch == ')')
                  --n;

            if (n<0) return i; //at any moment if expression is valid we cannot have more ) then (
       }
       return n>0 ? formula.length() : -1; 
  }
  //------------------------------------------------------------------------------
  /**
    Removes unnecessary outer brackets in an expression
  */
  protected static FastStr removeOuterBrackets(FastStr formula) {
    //has to be careful about (X+1)-(Y-1)
    //should not remove the outer brackets here thinking that they are unnecessary
    //but should remove when ((X+1)-(Y-1))
    FastStr temp = formula;
    int len = temp.length();
    while ((len>2) && (temp.charAt(0) == '(') && (temp.charAt(len-1) == ')') ) {
      temp = temp.substring(1, temp.length()-1).trim();
      if (checkBrackets(temp)==-1) { //if we did not screw up then assign to the return value
        formula = temp;
      }
      len = temp.length();
    }
    return formula;
  }
  
  private static final Pattern floatingPointPattern = Pattern.compile("^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$");
  private static final Pattern floatingPointPatternComma = Pattern.compile("^[-+]?[0-9]*,?[0-9]+([eE][-+]?[0-9]+)?$");
  //------------------------------------------------------------------------------
  protected boolean isValidDouble(FastStr formula) {
    //Double.valueOf(formula) thinks "020" is a valid double.
    if (formula.length()>1 && (formula.charAt(0)=='0' && formula.indexOf(m_DecimalSeparator)!=1)){
      return false;
    }
    if (m_DecimalSeparator == '.') {
      if (!floatingPointPattern.matcher(formula).matches()) {
      	return false;
      }
    } else {
      if (!floatingPointPatternComma.matcher(formula).matches()) {
      	return false;
      }
    }
    return true;
  }
  //------------------------------------------------------------------------------
  protected Node createParseTree(FastStr expToParse) throws ParserException {
  	/*
  	int brackets = CheckBrackets(formula);
    if (brackets>-1 && brackets<formula.length()){
    	throw new ParserException(translate.getMessage("BrcktMis", formula, String.valueOf(brackets)), formula.substring(brackets), formula);
    }
    else
  	if(brackets==formula.length()){
    	throw new ParserException(translate.getMessage("MisBrckt", formula), formula, formula);
  	}*/
    
  	expToParse = expToParse.trim(); //trim spaces.
  	// System.out.println(expToParse);
    if (expToParse.length()==0){
      return null; //if length is zero 
    }
    FastStr formula = removeOuterBrackets(expToParse); //remove unnecessary brackets

    //if we have removed brackets, check for spaces again:
    if(expToParse.length()!=formula.length()){
	    formula = formula.trim(); //trim spaces.
	    if (formula.length()==0){
	      return null; //if length is zero 
	    }
    }
    
    boolean isNum = isValidDouble(formula);
    //is this text a simple double value?    
    if (isNum)
    { //attach a double node in the structure
    	if (m_DecimalSeparator == '.') {
	      double number = Double.parseDouble(formula.toString());
	      return new BasicNode(number); //we create a double node and attach it to the *Node reference.
    	} else {
    		try {
					Number number = m_DecimalFormat.parse(formula.toString());
					return new BasicNode(number.doubleValue());
				} catch (ParseException e) {
					// should not happen, but if it happens, ignore and continue, we will report error
					// later when the string does not match anything else.
				}
    	}
    }

    //if it is not a simple double, maybe it is a variable?
    Node varNode = createVarNode(formula);
    if(varNode!=null){
    	return varNode;
    }
    
    //if it is not a variable
    Node leftNode = null, rightNode = null;
    int lastOper= findLastOper(formula);


    // if(lastOper>-1)
    //  System.out.println("Last operand is "+formula.charAt(lastOper));
      

    if (! (lastOper>0) ) //if it is 0 then it is a unary operation which is a one param function
    {
      _TOneParamParam param = new _TOneParamParam();
      if ( isOneParamFunc(formula, param, lastOper) )
      {
        if ((leftNode = createParseTree(param.param)) == null ){
             throw new ParserException(m_Translate.getMessage("ExpNtVld", param.param, formula), param.param.toString(), formula.toString());
             //return null;
        }
        //if it returns a function address, then we use that function, otherwise we will use the function name

        if (param.funcAddr != null) {
          return new NParamNode(new Node[] {leftNode}, param.funcAddr);
        }
        //if it is a one param function then we exit, otherwise below code will execute
      }
    }
    _TTwoParamParam param = new _TTwoParamParam();
    //if it is none of the above:
    if (isTwoParamFunc(formula, param, lastOper) )
    {
      if ((leftNode = createParseTree(param.paramLeft)) == null){
				throw new ParserException(m_Translate.getMessage("ExpNtVld", param.paramLeft, formula),
						param.paramLeft.toString(), formula.toString());
      }
      if ((rightNode = createParseTree(param.paramRight)) == null ){
				throw new ParserException(m_Translate.getMessage("ExpNtVld", param.paramRight, formula),
						param.paramRight.toString(), formula.toString());
      }
      //if there is a function address returned then we use it, otherwise we use function name
      if (param.funcAddr != null){
        return new NParamNode(new Node[] {leftNode, rightNode}, param.funcAddr);
      }
    }

    _TNParamParam Nparam = new _TNParamParam();
    //if it is none of the above:
    if (isNParamFunc(formula, Nparam, lastOper) )
    {
    	FastStr[] params = Nparam.params;
      if (params==null){
				throw new ParserException(m_Translate.getMessage("InvNPrm", formula), formula.toString(),
						formula.toString());
      }
      int nParam = params.length;
      Node[] nodes = new Node[nParam];
      for (int i=0; i<nParam; i++){
        if ( (leftNode = createParseTree(params[i]) )== null){
					throw new ParserException(m_Translate.getMessage("ExpNtVld", params[i], formula),
							params[i].toString(), formula.toString());
        }
        nodes[i] = leftNode;
      }

      //if there is a function address returned then we use it, otherwise we use function name
      if (Nparam.funcAddr != null){
        return new NParamNode(nodes, Nparam.funcAddr);
      }
    }
    //when code reaches here it means we did not return true so after compiling the expression.
    return null;
  }

	/**
	 * @param formula
	 */
	private Node createVarNode(FastStr formula) {
		@SuppressWarnings("unlikely-arg-type")
		Variable variable = (Variable)m_Variables.get(formula);
    if (variable!=null){
      //optimize constant nodes right away:      
      if(variable.isConstant){
        return new BasicNode(variable.value);
      }
      return new VarNode(variable); //recursion will end on these points when we get to the basics
    }
		else if(m_VariableResolver!=null)
		{
			int Len = formula.length();
			for(int i=0; i<Len; i++)
			{
				char ch = formula.charAt(i);
				if(!isValidChar(i, ch))
				{
					return null;
				}
			}
			return new UnknownVarNode(this, formula.toString());
		}
		return null;
	}
  //------------------------------------------------------------------------------
  private int findLastOper(FastStr formula) { //returns -1 if it cannot find anything
    int Precedence = 13; //There are 12 operands and 13 is higher then all
    int BracketLevel = 0; //shows the level of brackets we moved through
    int Result = -1;
    int Len = formula.length();
    int lastWasOperator = 0;

    for (int i = 0; i<Len; i++) //from left to right scan...
    {
      if (lastWasOperator>2){
        /*
        if(lastWasOperator==3){
          char ch = formula.charAt(i-2);
          if(!(ch=='<' || ch=='>')){
            return -1;
          }
        }
        else{
          return -1;
        }*/
        return -1;
      }
      switch (formula.charAt(i)) {
      case ')' :
        --BracketLevel; //counting bracket levels
        lastWasOperator = 0;
        break;
      case '(' :
        ++BracketLevel;
        lastWasOperator = 0;
        break;

      case '|' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 1 )
          {
             Precedence = 1;
             Result = i;
          }
        ++lastWasOperator;
        break;

      case '&' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 2 )
          {
             Precedence = 2;
             Result = i;
          }
        ++lastWasOperator;
        break;

      case '!' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 3 )
          {
             Precedence = 3;
             Result = i;
          }
        ++lastWasOperator;
        break;

      case '=' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 4 )
          {
             Precedence = 4;
             Result = i;
          }
        if(lastWasOperator>0){
          int prevOperIndex = i-lastWasOperator; 
          if(formula.charAt(prevOperIndex)=='<' || formula.charAt(prevOperIndex)=='>'){
            break; //skip incrementing lastWasOperator variable.
          }
        }
        ++lastWasOperator;
        break;

      case '>' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 5 )
          {
             Precedence = 5;
             Result = i;
          }
        if(lastWasOperator>0){
          if(formula.charAt(i-lastWasOperator)=='<'){
            break;
          }
        }
        ++lastWasOperator;
        break;

      case '<' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 5 )
          {
             Precedence = 5;
             Result = i;
          }
        ++lastWasOperator;
        break;

      case '-' :
        if (! (BracketLevel > 0 || lastWasOperator>0) ) //a main operation has to be outside the brackets
          if (Precedence >= 7)  //seeking for lowest precedence
          {
             Precedence = 7;
             Result = i; //record the current index.
          }
        ++lastWasOperator;
        break;
      case '+' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 7 )
          {
             Precedence = 7;
             Result = i;
          }
        ++lastWasOperator;
        break;
      case '%' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 9 )
          {
             Precedence = 9;
             Result = i;
          }
        ++lastWasOperator;
        break;
      case '/' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 9 )
          {
             Precedence = 9;
             Result = i;
          }
        ++lastWasOperator;
        break;
      case '*' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 9 )
          {
             Precedence = 9;
             Result = i;
          }
        ++lastWasOperator;
        break;
      case '^' :
        if (! (BracketLevel > 0 || lastWasOperator>0) )
          if (Precedence >= 12 )
          {
             Precedence = 12;
             Result = i;
          }
        ++lastWasOperator;
        break;

      case 'E' :
        if (i > 0 && lastWasOperator==0){
          char ch = formula.charAt(i-1);
          if (ch >= '0' && ch <= '9'){//this E may be part of a number in scientific notation.
            int j=i;
            while(j > 0) { //trace back.
              --j;
              ch = formula.charAt(j);
              if (ch==m_DecimalSeparator || (ch >= '0' && ch <= '9')){ //if it is not a function or variable name.
                continue;
              }
              if (ch=='_' || (ch>='A' && ch<='Z')){//is it a func or var name?
                lastWasOperator = 0;
                break; //break the while loop.
              }
              ++lastWasOperator; //it must be an operator or a paranthesis.
              break; //break the while loop.
            }
            if (j==0 && (ch >= '0' && ch <= '9')){
              ++lastWasOperator;
            }
          }else{
            lastWasOperator = 0;
          }
        }else {
          lastWasOperator = 0;
        }
      break;
			case ' ': //space:
				break;
      default :
        lastWasOperator=0;
      }
    }
    return Result;
  }
  //------------------------------------------------------------------------------
  private boolean isTwoParamFunc( FastStr formula,
                           _TTwoParamParam paramByRef,
                           int CurrChar //gives the last operation index in the string
                           )
  {
    int Len= formula.length();

    if (CurrChar>0 ) //if function in question is an operand
    {
      if(CurrChar>Len-2){
        return false;
      }
      char currCh = formula.charAt(CurrChar);
       
      //was it an operand also? we want to find <>, >=, <=
      if(currCh=='<') {
        char nextCh = formula.charAt(CurrChar+1); //look ahead.
        if(nextCh=='>'){
          paramByRef.funcAddr = notequals_;
          paramByRef.paramLeft = formula.substring(0, CurrChar);
          paramByRef.paramRight= formula.substring(CurrChar+2, formula.length());
        }else if (nextCh=='='){
          paramByRef.funcAddr = ltequals_;
          paramByRef.paramLeft = formula.substring(0, CurrChar);
          paramByRef.paramRight= formula.substring(CurrChar+2, formula.length());
        }
        else {
          paramByRef.funcAddr = less_; //default case.
          paramByRef.paramLeft = formula.substring(0, CurrChar);
          paramByRef.paramRight= formula.substring(CurrChar+1, formula.length());
        }
  
        if (! (paramByRef.paramLeft.length()>0) ) {
          return false;
        }
        if (! (paramByRef.paramRight.length()>0) ) {
          return false;
        }
        return true; //all output is assigned, now we return true.
      }
      else if(currCh=='>') {
        char nextCh = formula.charAt(CurrChar+1);
        if(nextCh=='='){
          paramByRef.funcAddr = gtequals_;
          paramByRef.paramLeft = formula.substring(0, CurrChar);
          paramByRef.paramRight= formula.substring(CurrChar+2, formula.length());
        }
        else {
          paramByRef.funcAddr = greater_; //default case.
          paramByRef.paramLeft = formula.substring(0, CurrChar);
          paramByRef.paramRight= formula.substring(CurrChar+1, formula.length());
        }
        if (! (paramByRef.paramLeft.length()>0) ) {
          return false;
        }
        if (! (paramByRef.paramRight.length()>0) ) {
          return false;
        }
        return true; //all output is assigned, now we return true.
      }
      else {        
        paramByRef.paramLeft = formula.substring(0, CurrChar);
        if (! (paramByRef.paramLeft.length()>0) ) {
          return false;
        }
  
        paramByRef.paramRight= formula.substring(CurrChar+1, formula.length());
        if (! (paramByRef.paramRight.length()>0) ) {
          return false;
        }
  
        switch (currCh) {
          //analytical operators:
          case	'+': paramByRef.funcAddr = add_; break;
          case	'-': paramByRef.funcAddr = subtract_; break;
          case	'*': paramByRef.funcAddr = multiply_; break;
          case	'/': paramByRef.funcAddr = divide_; break;
          case	'^': paramByRef.funcAddr = power_; break;
          case	'%': paramByRef.funcAddr = intdiv_; break;
  
          //logical operators:
          case	'=': paramByRef.funcAddr = equals_; break;
          case	'&': paramByRef.funcAddr = and_; break;
          case	'|': paramByRef.funcAddr = or_; break;
        }
      }
      return true; //all output is assigned, now we return true.
    }
    //if we reach here, result is false
    //if main operation is not an operand but a function
    int BracketLevel, paramStart;
    int i;
    StringBuilder temp = new StringBuilder();
    if (formula.charAt(Len-1) == ')')  //last character must be brackets closing function param list
    {
      i= 0;
      while (isValidChar(i, formula.charAt(i)))
      {
        temp.append(formula.charAt(i));
        ++i;
      }
      if ((formula.charAt(i) == '(') && (i < Len-1) )
      {
        //temp.charAt(i) = 0;
        IFunction f = (IFunction)m_Functions.get(temp.toString());
        if (f != null && f.getNumberOfParams()==2)
        {
          //paramByRef.funcAddr = twoParamFunc;
          paramStart= i+1;
          BracketLevel= 1;
          while (! (i>Len-1-1) ) //last character is a ')', that's why we use i>Len-1
          {
            ++i;
            char ch = formula.charAt(i);
            switch( ch ) {
              case '(': ++BracketLevel; break;
              case ')': --BracketLevel; break;
              case ',':
              case ':':
                if (m_FunctionParamSeparator == ch && (1 == BracketLevel) && (i<Len-2) ) //last character is a ')', that's why we use i>Len-2
                {
                  paramByRef.paramLeft= formula.substring(paramStart, i);
                  paramByRef.paramRight= formula.substring(i+1, Len-1); //last character is a ')', that's why we use Len-1-i
                  paramByRef.funcAddr = f;
                  return true; //we are sure that it is a two parameter function
                }
                break;
            }
          }
        }
      }
    }
    return false; //means we could not find it
  }
  //------------------------------------------------------------------------------
	private boolean isOneParamFunc(FastStr formula, _TOneParamParam paramByRef, int lastOpIndex)  {
    int paramStart;
    int len= formula.length();
    if (lastOpIndex == 0) //if function in question is an unary operand
    {
      paramByRef.param= formula.substring(1, formula.length());
      if (! (paramByRef.param.length()>0) ) {
        return false;
      }

      switch (formula.charAt(lastOpIndex)) {
        case	'+': paramByRef.funcAddr = unaryadd_; break;
        case	'-': paramByRef.funcAddr = negate_; break;
        case  '!': paramByRef.funcAddr = not_; break;
        default: return false; //only + and - can be unary operators
      }
      return true; //all output is assigned, now we exit.
    }
    //if we reach here, result is false
    //if main operation is not an operand but a function
    if (formula.charAt(len-1) == ')') //last character must be brackets closing function param list
    {
      int i= 0;
      StringBuilder temp = new StringBuilder();
      while (isValidChar(i, formula.charAt(i)))
      {
        temp.append(formula.charAt(i));
        ++i;
      }
      while(formula.charAt(i) == ' '){ //skip spaces.
      	++i;
      }
      if ((formula.charAt(i) == '(') && (i < len-2) )
      {
        IFunction f = (IFunction)m_Functions.get(temp.toString());
        if (f != null && f.getNumberOfParams()==1)
        {
         paramStart= i+1;
         //paramByRef.funcAddr = oneParamFunc;
         paramByRef.param= formula.substring(paramStart, len-1); //check example: SIN(30)
         paramByRef.funcAddr = f; 
         return true; //we are sure that it is a two parameter function
        }
      }
    }
    return false;
  }
  //------------------------------------------------------------------------------
	private boolean isNParamFunc(FastStr formula, _TNParamParam paramByRef, int lastOpIndex)  {
    //if main operation is not an operand but a function
    int len= formula.length();
    int bracketLevel, paramStart;
    int i;
    StringBuilder temp = new StringBuilder();
    if (formula.charAt(len-1) == ')')  //last character must be brackets closing function param list
    {
      i= 0;
      while (isValidChar(i, formula.charAt(i)))
      {
        temp.append(formula.charAt(i));
        ++i;
      }
      while(formula.charAt(i) == ' '){ //skip spaces.
      	++i;
      }
      if ((formula.charAt(i) == '(') && (i < len-1) )
      {
        paramByRef.funcAddr = (IFunction)m_Functions.get(temp.toString());
        if (paramByRef.funcAddr != null)
        {
          int nParams = paramByRef.funcAddr.getNumberOfParams();
          if(nParams>-1) {
            FastStr[] params = new FastStr[nParams];
            paramByRef.params = params;
            paramStart = i+1;
            bracketLevel = 1;
            int pIndex = 0;
            while (! (i>len-1-1) ) //last character is a ')', that's why we use i>Len-1
            {
              ++i;
              char ch = formula.charAt(i);
              switch( ch ) {
                case '(': ++bracketLevel; break;
                case ')': --bracketLevel; break;
                case ',':
                case ':':
                  if ((m_FunctionParamSeparator == ch) && (1 == bracketLevel) && (i<len-2) ) //last character is a ')', that's why we use i>Len-2
                  {
                    //must have at least 2 params for this part to work:
                    if (! (pIndex<nParams) ){
                      return false; //wrong number of parameters.
                    }
                    params[pIndex++] = formula.substring(paramStart, i);
                    //System.out.println("N Parameters are: " + toString(params));
                    if(pIndex==nParams-1){
                      //assign the last one:
                      params[pIndex] = formula.substring(i+1, len-1);
                      return true;
                    }
                    paramStart = i+1;
                  }
              }
            }
          }else {
						ArrayList<FastStr> list = new ArrayList<FastStr>();
						paramStart	= i+1;
						bracketLevel= 1;
						boolean insideStringLiteral = false;
						while (! (i>len-1-1) ) //last character is a ')', that's why we use i>Len-1
						{
							++i;
							char ch = formula.charAt(i);
							switch( ch ) 
							{
								case '"': insideStringLiteral = !insideStringLiteral; break;
								case '(': if(!insideStringLiteral) ++bracketLevel; break;
								case ')': if(!insideStringLiteral) --bracketLevel; break;
								case ',':
								case ':':
									if (m_FunctionParamSeparator == ch && !insideStringLiteral && (1 == bracketLevel) && (i<len-2) ) //last character is a ')', that's why we use i>Len-2
									{
										list.add( formula.substring(paramStart, i) );
										paramStart = i+1;
									}
									break;
							}
						}
						//add the remaining:
						FastStr remaining = formula.substring(paramStart, len-1).trim();
						if(remaining.length()>0){
							list.add(remaining);
						}
						paramByRef.params = (FastStr[])list.toArray(new FastStr[list.size()]);
						return true;
          }
          
        }
      }
    }
    //System.out.println("Could not match a function for " + formula);
    return false; //means we could not find it
  }
  //------------------------------------------------------------------------------
  //to help debug:
  /*
  private String toString(String[] p){
    StringBuilder sb = new StringBuilder("[ ");
    for (int i=0; i<p.length; i++){
      sb.append(p[i]);
      if (i<p.length-1){
        sb.append(", ");
      }
    }
    sb.append(" ]");
    return sb.toString();
  }*/
  //------------------------------------------------------------------------------
  static Node optimizeNode(Node aNode) {
    aNode.optimize();
    if (aNode instanceof NParamNode) { //could be a VarNode which cannot be optimized.
      NParamNode nParamNode = (NParamNode)aNode;
      Node[] nodes = nParamNode.nodes;

      for (int i=0; i<nodes.length; i++){
        //if any node is not constant, just return as is:
        if ( !(nodes[i] instanceof BasicNode) ) {
          return aNode; //it is not optimizable.
        }
      }
      //if all parameters of the function are constants (basic nodes):
      return new BasicNode(nParamNode.getValue());
    }
    return aNode;
  }
  //------------------------------------------------------------------------------
  public void optimize() {
    m_Node = optimizeNode(m_Node);
  }

  /**
    Sorts a given array of objects local sensitively based on each element's
    toString() result. Non of the objects should be null.
  */
  class Sort extends QuickSort{
    String[] a;
    Collator collator = Collator.getInstance(m_Locale);
    public String[] sort(String[] objs){
      if (objs.length>1){
        a = objs;
        super.sort(0, objs.length-1);
      }
      return objs;
    }

    public int compareTo(int i, int j){
      return collator.compare(a[i], a[j]);
    }
    public void swap(int i, int j){
      String temp = a[i];
      a[i] = a[j];
      a[j] = temp;
    }
  }

	public IVariableResolver getVariableResolver() {
		return m_VariableResolver;
	}

	public void setVariableResolver(IVariableResolver variableResolver) {
		m_VariableResolver = variableResolver;
		if(variableResolver==null){
			m_Dirty = true; //what was valid before could now be invalid. 
		}
	}

	public char getDecimalSeparator() {
		return m_DecimalSeparator;
	}

	public char getFunctionParamSeparator() {
		return m_FunctionParamSeparator;
	}

} //End of Math Parser Implementation.  
  

//helper classes to return multiple values from function calls:
class _TNParamParam {
  IFunction funcAddr;
  FastStr[] params;
}
class _TOneParamParam {
  IFunction funcAddr;
  FastStr param;
}
class _TTwoParamParam {
  IFunction funcAddr;
  FastStr paramLeft, paramRight;
}

//internal representation of a variable.
class Variable {
  public boolean isConstant;
  public double value;
  @SuppressWarnings("unused")
private Variable(){
  }
  public Variable(double newVal){
    value = newVal;
  }
}

//N parameter functions such as IF(X, Y, Z) etc
class NParamNode extends Node {
  public Node[] nodes;
  public IFunction fPtr;

  public NParamNode(Node[] n, IFunction FuncAddr){
    nodes = n;
    fPtr  = FuncAddr;
  }
  public double getValue() {
    return fPtr.run(nodes);
  }
  public boolean isUsed(Object Addr){
    for (int i=0; i<nodes.length; i++){
      if (nodes[i].isUsed(Addr)){
        return true;
      }
    }
    return Addr.equals(fPtr);
  }
  public void optimize() { //Optimize evaluates constant values at compile time.
    for (int i=0; i<nodes.length; i++){
      nodes[i] = MathParserImpl.optimizeNode(nodes[i]);
    }
  }
}

//a variable node
class VarNode extends Node {
  public Variable pVar;   //address of the variable in the variable list
  public VarNode(Variable variable) {
    pVar = variable;
  }
  public double getValue() {
    return pVar.value;
  }
  public boolean isUsed(Object Addr) {
    return (Addr.equals(pVar));
  }
  //since there is no parameter used to get the value of a variable, no further
  //optimization can be made.
  public void optimize() //Optimize evaluates constant values at compile time.
  {
    //do nothing
  }
}

//a variable that was not defined prior to parsing.
class UnknownVarNode extends Node {
  protected String m_VarName; 
  private MathParserImpl m_MathParser;
  public UnknownVarNode(MathParserImpl p, String varName) {
    m_VarName = varName;
    m_MathParser = p;
  }
  public double getValue() {
    return m_MathParser.m_VariableResolver.getValue(m_MathParser, m_VarName);
  }
  public boolean isUsed(Object Addr) {
  	if(Addr instanceof String){
  		return m_VarName.equals(Addr);
  	}
    return false;
  }
  //since there is no parameter used to get the value of a variable, no further
  //optimization can be made.
  public void optimize() //Optimize evaluates constant values at compile time.
  {
    //do nothing
  }
}

////////////////////////////////////////////////////////////////////////////////
//Non-public classes that represent tree nodes in the parse tree

//These classes need to be in separate files for older JVMs not to give verification and
//access errors:
//Logical functions (operators):
class __greater extends TwoParamFunc {
  public double  run(IParameter[] p){
      return ( p[0].getValue() > p[1].getValue() ) ? 1 : 0;
  }
}

class __less extends TwoParamFunc {
  public double  run(IParameter[] p){
      return ( p[0].getValue() < p[1].getValue() ) ? 1 : 0;
  }
}

//<> operator:
class __notequals extends TwoParamFunc {
  public double  run(IParameter[] p){
      return ( p[0].getValue() != p[1].getValue() ) ? 1 : 0;
  }
}
//<= operator:
class __ltequals extends TwoParamFunc {
  public double  run(IParameter[] p){
      return ( p[0].getValue() <= p[1].getValue() ) ? 1 : 0;
  }
}
//>= operator:
class __gtequals extends TwoParamFunc {
  public double  run(IParameter[] p){
      return ( p[0].getValue() >= p[1].getValue() ) ? 1 : 0;
  }
}

class __equals extends TwoParamFunc {
  public double  run(IParameter[] p){
      return ( p[0].getValue() == p[1].getValue() ) ? 1 : 0;
  }
}

class __or extends TwoParamFunc {
  public double  run(IParameter[] p){
    return (( p[0].getValue() > 0 ) || (p[1].getValue() > 0 ) ) ? 1 : 0;
  }
}

class __and extends TwoParamFunc {
  public double  run(IParameter[] p){
    return ( (p[0].getValue() > 0) && (p[1].getValue() > 0) ) ? 1 : 0;
  }
}

class __not extends OneParamFunc {
  public double run(IParameter[] p){
       return (p[0].getValue()==0) ? 1 : 0;
  }
}

//Analytical functions:
class __unaryadd extends OneParamFunc {
  public double run(IParameter[] p){
       return p[0].getValue();
  }
}

class __add extends TwoParamFunc {
  public double  run(IParameter[] p){
      return p[0].getValue() + p[1].getValue();
  }
}

class __subtract extends TwoParamFunc {
  public double  run(IParameter[] p){
      return p[0].getValue() - p[1].getValue();
  }
}

class __multiply extends TwoParamFunc {
  public double  run(IParameter[] p){
      return p[0].getValue() * p[1].getValue();
  }
}

class __divide extends TwoParamFunc {
  public double run(IParameter[] p){
      return p[0].getValue() / p[1].getValue();
  }
}

class __modulo extends TwoParamFunc {
  public double run(IParameter[] p){
    int p1 = (int)Math.floor(p[0].getValue());
    int p2 = (int)Math.floor(p[1].getValue());
    return p1 % p2;
  }
}

class __intdiv extends TwoParamFunc {
  public double run(IParameter[] p){
      return Math.floor(Math.floor(p[0].getValue()) / Math.floor(p[1].getValue()));
  }
}

class __negate extends OneParamFunc {
  public double run(IParameter[] p){
      return -p[0].getValue();
  }
}

class __intpower extends TwoParamFunc {
  public double run(IParameter[] p){
      return Math.pow(p[0].getValue(), Math.floor(p[1].getValue()));
  }
}

class __square extends OneParamFunc {
  public __square(){}
  public double run(IParameter[] p){
      double d = p[0].getValue();
      return (d*d);
  }
}

class __power extends TwoParamFunc {
  public double run(IParameter[] p){
      return Math.pow(p[0].getValue(), p[1].getValue());
  }
}

class __sin extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.sin(p[0].getValue());
  }
}

class __cos extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.cos(p[0].getValue());
  }
}

class __arctan extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.atan(p[0].getValue());
  }
}

class __sinh extends OneParamFunc {
  public double run(IParameter[] p){
      double d = p[0].getValue();
      return (Math.exp(d)-Math.exp(-d))*0.5;
  }
}

class __cosh extends OneParamFunc {
  public double run(IParameter[] p){
      double x__ = p[0].getValue();
      return (Math.exp(x__)+Math.exp(-x__))*0.5;
  }
}

class __cotan extends OneParamFunc {
  public double run(IParameter[] p){
      return 1/Math.tan(p[0].getValue());
  }
}

class __tan extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.tan(p[0].getValue());
  }
}

class __exp extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.exp(p[0].getValue());
  }
}

class __ln extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.log(p[0].getValue());
  }
}

class __log10 extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.log(p[0].getValue())/Math.log(10);
  }
}

class __log2 extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.log(p[0].getValue())/Math.log(2);
  }
}

class __logN extends TwoParamFunc {
  public double run(IParameter[] p){
      return Math.log(p[0].getValue())/Math.log(p[1].getValue());
  }
}

class __sqrt extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.sqrt(p[0].getValue());
  }
}

class __abs extends OneParamFunc {
  public double run(IParameter[] p){
      return Math.abs(p[0].getValue());
  }
}

class __min implements IFunction {
  public double run(IParameter[] p){
  	if (p.length < 2) {
  		throw new IllegalArgumentException("MIN function requires at least 2 parameters.");
  	}
    double min = p[0].getValue();
    for (int i = 1; i< p.length; i++) {
    	double val = p[i].getValue();
    	if (val < min) {
    		min = val;
    	}
    }
    return min;
  }

	public int getNumberOfParams() {
		return -1; // any number of parameters
	}
}

class __max implements IFunction {
  public double run(IParameter[] p){
  	if (p.length < 2) {
  		throw new IllegalArgumentException("MAX function requires at least 2 parameters.");
  	}
    double max = p[0].getValue();
    for (int i = 1; i< p.length; i++) {
    	double val = p[i].getValue();
    	if (val > max) {
    		max = val;
    	}
    }
    return max;
  }

	public int getNumberOfParams() {
		return -1; // any number of parameters
	}
}

class __sign extends OneParamFunc {
  public double run(IParameter[] p){
    double x__= p[0].getValue();
    if (x__ < 0)
      return -1;
    else
      if (x__ > 0)
        return 1.0;
      else
        return 0.0;
  }
}

class __trunc extends OneParamFunc {
  public double run(IParameter[] p){
    double x__ = p[0].getValue();
    if (x__>=0){
      return Math.floor(x__);
    }
    else {
      return Math.ceil(x__);
    }
  }
}

class __ceil extends OneParamFunc {
  public double run(IParameter[] p){
    return Math.ceil(p[0].getValue());
  }
}

class __floor extends OneParamFunc {
  public double run(IParameter[] p){
    return Math.floor(p[0].getValue());
  }
}

class __rnd extends OneParamFunc {
  public double run (IParameter[] p){
      double x__ = p[0].getValue();
      if (x__>=0)
        return Math.floor(Math.random() * Math.floor(x__));
      else
        return Math.ceil(Math.random() * Math.ceil(x__));
  }
}

class __random extends OneParamFunc {
  public double run (IParameter[] p){
    return Math.random() * p[0].getValue();
  }
}

class __if implements IFunction {
  public double run (IParameter[] p){
    return (p[0].getValue()!=0.0) ? p[1].getValue() : p[2].getValue();
  }
  public int getNumberOfParams(){
    return 3;
  }
}

class __sum implements IFunction {
  public double run (IParameter[] p){
    double total = 0;
    for(int i=0; i<p.length; i++) {
      total += p[i].getValue();
    }
    return total;
  }
  public int getNumberOfParams(){
    return -1;
  }
}



//constants such as 3, 5 7 in the formula
class BasicNode extends Node {
  public double Value;

  public BasicNode(double Val){
    Value = Val;
  }
  public double getValue() {
    return Value;
  }
  //------------------------------------------------------------------------------
  public boolean isUsed(Object Addr) {
    return false; //a basic node does not store any variable or function info
  }
  //------------------------------------------------------------------------------
  public boolean IsUsed(String Name) {
    return false; //a basic node does not store any variable or function info
  }
  //------------------------------------------------------------------------------
  //since basic node cannot be optimized further, this function does nothing
  public void optimize(){ //Optimize evaluates constant values at compile time.
    //do nothing.
  }
}
