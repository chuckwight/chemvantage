/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
*   
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import java.io.Serializable;
import java.text.Collator;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.bestcode.mathparser.IMathParser;
import com.bestcode.mathparser.MathParserFactory;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Ignore;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.condition.IfNotZero;

@Entity
public class Question implements Serializable, Cloneable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index(IfNotZero.class)	long topicId;
	@Index	String assignmentType;
			String text;
			String type;
			int nChoices=0;
			List<String> choices = new ArrayList<String>();
			double requiredPrecision=0;
			int significantFigures = 0;
			String correctAnswer;
			String tag;
	@Index	int pointValue=1;
			String parameterString;
			String hint;
			String solution;
			String authorId;
			String contributorId;
			String editorId;
			String notes;
			String learn_more_url;
			boolean scrambleChoices;
			// Note: the parameters array formerly had the attribute @Transient javax.persistence.Transient
	@Ignore		int[] parameters = {0,0,0,0};
	@Index		boolean isActive = false;
	
	public static final int MULTIPLE_CHOICE = 1;
	public static final int TRUE_FALSE = 2;
	public static final int SELECT_MULTIPLE = 3;
	public static final int FILL_IN_WORD = 4;
	public static final int NUMERIC = 5;
	

	Question() {}

	Question(int t) {
		switch (t) {
		case (1): this.type = "MULTIPLE_CHOICE"; break;
		case (2): this.type = "TRUE_FALSE"; break;
		case (3): this.type = "SELECT_MULTIPLE"; break;
		case (4): this.type = "FILL_IN_WORD"; break;
		case (5): this.type = "NUMERIC";
				  this.significantFigures = 3;
				  this.requiredPrecision = 2; // percent
				  break;
		default:  this.type = null;
		}
		this.correctAnswer = "";
		this.parameterString = "";
		this.pointValue = 1;
		this.isActive = false;
	}

	Question (long topicId,String text,String type,int nChoices,List<String> choices,
			double requiredPrecision,int significantFigures,String correctAnswer,String tag,int pointValue,String parameterString,
			String hint,String solution,String authorId,String contributorId,String editorId,String notes) {
		this.topicId = topicId;
		this.text = text;
		this.type = type;
		this.nChoices = nChoices;
		this.choices = choices;
		this.requiredPrecision = requiredPrecision;
		this.significantFigures = significantFigures;
		this.correctAnswer = correctAnswer;
		this.tag = tag;
		this.pointValue = pointValue;
		this.parameterString = parameterString;
		this.hint = hint;
		this.solution = solution;
		this.authorId = authorId;
		this.contributorId = contributorId;
		this.editorId = editorId;
		this.notes = "";
		this.isActive = false;
	}

	public void validateFields() {
		if (assignmentType==null) assignmentType="";
		if (text==null) text="";
		if (type==null) type="";
		if (correctAnswer==null) correctAnswer="";
		if (tag==null) tag="";
		if (parameterString==null) parameterString="";
		if (hint==null) hint="";
		if (solution==null) solution="";
		if (authorId==null) authorId="";
		if (contributorId==null) contributorId="";
		if (editorId==null) editorId="";
		if (notes==null) notes="";
	}
	public Long getId() {
		return this.id;
	}
	
	public int getPointValue() {
		return this.pointValue;
	}
	
	public String getCorrectAnswer() {
		switch (getQuestionType()) {
		case 4: // FILL-IN-WORD
			String[] answers = correctAnswer.split(",");
			return answers[0];
		case 5: // NUMERIC
			return parseString(correctAnswer);
		default: return correctAnswer;
		}
	}
	
	public boolean requiresParser() {
		return text.contains("#") || this.parameterString.length()>0;
	}
	
	public void setParameters() {
		if (this.requiresParser()) setParameters(-1); // set parameters with a random seed based on the current time
	}

	public void setParameters(long seed) {
		if (!this.requiresParser()) return;     // bulletproofing
		if (this.parameterString==null || this.parameterString.isEmpty()) {
			this.parameterString = "";
			return;
		}

		Random rand = new Random();
		// use seed=-1 for randomly fluctuating parameters, non-zero for deterministic pseudo-random
		if (seed != -1) rand.setSeed((long)seed);

		char p = 'a';
		while (p <= 'd') {
			try { // choose parameters from a string like "a 3:6 b 5:8 c -3:3 d 1:1"
				int i = parameterString.indexOf(p);       // find the index of parameter 'a'
				int j = parameterString.indexOf(':',i);   // find the index of the separator
				int k = parameterString.indexOf(" ",j);   // find the trailing white space
				if (k < 0) k = parameterString.length();  // or the end of the string
				int low = Integer.parseInt(parameterString.substring(i+2,j)); // extract the low limit 3
				int hi = Integer.parseInt(parameterString.substring(j+1,k));  // extract the high limit 6
				parameters[p-'a'] = low + rand.nextInt(hi-low+1);             // randomly select the parameter
				p++;                                      // repreat the process for the next parameter
			}
			catch (Exception e) {
				parameters[p-'a'] = 1;    // if exception is thrown, set parameter value to 1
				p++;                      // and try the next parameter
			}
		}  
	}
	
	int getNumericItemType() {
		if (requiredPrecision==0.0 && significantFigures==0) return 0;      // Q: rules/format  A: exact value match
		else if (requiredPrecision==0.0 && significantFigures!=0) return 1; // Q: show sig figs A: exact value match
		else if (requiredPrecision!=0.0 && significantFigures==0) return 2; // Q: rules/format  A: value agrees to %
		else if (requiredPrecision!=0.0 && significantFigures!=0) return 3; // Q: show sig figs A: value agrees to %
		return 0; //default case
	}
	
	public String parseString(String raw) {
		int itemType = getNumericItemType();
		switch (itemType) {
			case 0: return parseString(raw,1);  // return with historical rules-based display
			case 1: return parseString(raw,2);  // output with sig figs
			case 2: return parseString(raw,1);  // output with historical rules-based display
			case 3: return parseString(raw,2);  // output with sig figs
			default: return "";  // invalid item type
		}
	}
	
	public String parseString(String raw, int outputType) {  
		// this section uses a fully licensed version of the Jbc Math Parser
		// from bestcode.com (license purchased by C. Wight on Nov 18, 2007)

		raw = parseFractions(raw);
		
		IMathParser parser = MathParserFactory.create();
		try {
			parser.setVariable("a",parameters[0]);
			parser.setVariable("b",parameters[1]);
			parser.setVariable("c",parameters[2]);
			parser.setVariable("d",parameters[3]);
		}
		catch (Exception e) { // parser parameters not set properly; bail on parsing
			return raw==null?"":raw;
		}

		if (raw == null) raw = "";
		String[] pieces = raw.split("#");
		StringBuffer buf = new StringBuffer();
		
		for (int i=0;i<pieces.length;i++) {
			try {
				parser.setExpression(pieces[i]);
				double value = parser.getValue();
				String fmt = "%." + significantFigures + "G";
				/*  There are three styles of parser output:
				 *  0 - raw output from the JbcParser unit
				 *  1 - ChemVantage historical rules-based output for integers, floats and exponential styles
				 *  2 - Output containing the specified number of significant digits
				 */
				switch (outputType) {
					case 0:	buf.append(value);   //buf.append(pieces[i]);
							break;
					case 1:	DecimalFormat df = new DecimalFormat();
							if ((Math.abs(value) < 100) && (value - Math.floor(value) == 0)) df.applyPattern("0"); // small integer output
							else if ((Math.abs(value) < 1.0E5) && (Math.abs(value) > 1.0E-2)) df.applyPattern("0.0#####"); // use decimal number
							else df.applyPattern("0.####E0"); // use scientific notation
							buf.append(df.format(value)); 
							break;
					case 2: buf.append(String.format(fmt,value));
							break;
					case 3: buf.append(String.format(fmt,value));
							break;
					default:		
				}
			} catch (Exception e) {
				buf.append(pieces[i]);  // expression could not be parsed; probably text - return unchanged
			}
		}
		return buf.toString();
	}
	
	public String print() {
		return print("","");
	}
	
	public String print(String showWork,String studentAnswer) {
		StringBuffer buf = new StringBuffer();
		char choice = 'a';
		List<Character> choice_keys = new ArrayList<Character>();
		Random rand = new Random();
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append(text + "<br/>");
			for (int i=0; i<nChoices; i++) choice_keys.add(Character.valueOf((char)('a'+i)));
			buf.append("<span style='color:#EE0000;font-size: small;'>Select only the best answer:</span><br/>");
			while (choice_keys.size()>0) {
				choice = choice_keys.remove(scrambleChoices?rand.nextInt(choice_keys.size()):0);
				buf.append("<label><input type=radio name=" + this.id + " value=" + choice + (studentAnswer.indexOf(choice)>=0?" CHECKED />":" />") + choices.get(choice-'a') + "</label><br/>");
			}
			buf.append("<br/>");
			break;
		case 2: // True/False
			buf.append(text);
			buf.append("<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select true or false:</span><br/>");
			buf.append("<label><input type=radio name=" + this.id + " value='true'" + (studentAnswer.equals("true")?" CHECKED />":" />") + " True</label><br/>");
			buf.append("<label><input type=radio name=" + this.id + " value='false'" + (studentAnswer.equals("false")?" CHECKED />":" />") + " False</label><br/>");
			buf.append("<br/>");
			break;
		case 3: // Select Multiple
			buf.append(text + "<br/>");
			for (int i=0; i<nChoices; i++) choice_keys.add(Character.valueOf((char)('a'+i)));
			buf.append("<span style='color:#EE0000;font-size: small;'>Select all of the correct answers:</span><br/>");
			while (choice_keys.size()>0) {
				choice = choice_keys.remove(scrambleChoices?rand.nextInt(choice_keys.size()):0);
				buf.append("<label><input type=checkbox name=" + this.id + " value=" + choice + (studentAnswer.indexOf(choice)>=0?" CHECKED />":" />") + choices.get(choice-'a') + "</label><br/>");
			}
			buf.append("<br/>");
			break;
		case 4: // Fill-in-the-Word
			buf.append(text);
			buf.append("<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Enter the correct word or phrase:</span><br/>");
			buf.append("<label><input id=" + this.id + " type=text name=" + this.id + " value='" + quot2html(studentAnswer) + "' />");
			buf.append("&nbsp;" + tag + "</label><br/><br/>");
			break;
		case 5: // Numeric Answer
			buf.append(parseString(text));
			buf.append("<br/>");
			buf.append("<div id=showWork" + this.id + " style='display:none'>"
					+ "<label>Show your work:<br/><TEXTAREA NAME=ShowWork" + this.id + " ROWS=5 COLS=50 WRAP=SOFT "
					+ "onkeyup=this.value=this.value.substring(0,500); placeholder='Show your work here'>" + (showWork==null?"":showWork) + "</TEXTAREA>"
					+ "</label><br/></div>");
			buf.append("<label>");
			switch (getNumericItemType()) {
			case 0: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the exact value.</span><br/>"); break;
			case 1: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 2: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value to within " + requiredPrecision + "% precision. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 3: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			default:
			}			
			buf.append("<input size=25 type=text name=" + this.id + " id=answer" + this.id + " value='" + studentAnswer + "' onFocus=showWorkBox('" + this.id + "'); />");
			buf.append("&nbsp;" + parseString(tag) + "</label><br/><br/>");
			break;        
		}
		return buf.toString();
	}

	String getHint() {
		return parseString(hint) + "<br/>";
	}
	
	String printAll() {
		// use this method to display an example of the question, correct answer and solution
		StringBuffer buf = new StringBuffer();
		char choice = 'a';
		List<Character> choice_keys = new ArrayList<Character>();
		for (int i=0; i<nChoices; i++) choice_keys.add(Character.valueOf((char)('a'+i)));
		Random rand = new Random();
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select only the best answer:</span><UL>");
			while (choice_keys.size()>0) {
				choice = choice_keys.remove(scrambleChoices?rand.nextInt(choice_keys.size()):0);
				buf.append("<LI>" 
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ quot2html(choices.get(choice-'a'))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>")
						+ "</LI>");
			}
			buf.append("</UL>");
			break;
		case 2: // True/False
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select true or false:</span><UL>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("true")?"<B>True</B>":"<FONT COLOR=#888888>True</FONT>") 
					+ "</LI>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("false")?"<B>False</B>":"<FONT COLOR=#888888>False</FONT>") 
					+ "</LI>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select all of the correct answers:</span><UL>");
			while (choice_keys.size()>0) {
				choice = choice_keys.remove(scrambleChoices?rand.nextInt(choice_keys.size()):0);
				buf.append("<LI>"
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ quot2html(choices.get(choice-'a'))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>")
						+ "</LI>");
			}
			buf.append("</UL>");
			break;
		case 4: // Fill-in-the-Word
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Enter the correct word or phrase:</span><br/>");
			buf.append("<span style='border: 1px solid black'>"
					+ "<b>" + (this.hasACorrectAnswer()?quot2html(correctAnswer):"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;") + "</b>"
					+ "</span>");
			buf.append("&nbsp;" + tag + "<br/><br/>");
			break;
		case 5: // Numeric Answer
			buf.append(parseString(text) + "<br/>");
			switch (getNumericItemType()) {
			case 0: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the exact value.</span><br/>"); break;
			case 1: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 2: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value to within " + requiredPrecision + "% precision. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 3: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			default:
			}
			buf.append("<span style='border: 1px solid black'>"
					+ "<b>" + (this.hasACorrectAnswer()?getCorrectAnswer():"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;") + "</b>"
					+ "</span>");
			buf.append("&nbsp;" + parseString(tag) + "<br/><br/>");
			if (hint.length()>0) {
				buf.append("Hint: " + parseString(hint) + "<br/><br/>");
			}
			if (solution.length()>0) {
				buf.append("Solution:<br/>" + parseString(solution));
			}
			buf.append("<br/>");
			break;        
		}
		return buf.toString();
	}

	String printAllToStudents(String studentAnswer) {
		return printAllToStudents(studentAnswer,true,"");
	}
	
	String printAllToStudents(String studentAnswer,boolean showDetails) {
		return printAllToStudents(studentAnswer,showDetails,"");
	}
	
	String printAllToStudents(String studentAnswer,boolean showDetails,String showWork) {
		// use this method to display an example of the question, correct answer and solution
		// this differs from printAll() because only the first of several 
		// correct fill-in-word answers is presented, and choices are not scrambled
		// showDetails enables display of Solution to numeric problems (default = true)
		StringBuffer buf = new StringBuffer("<a name=" + this.id + "></a>");
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select only the best answer:</span><br/>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("&nbsp;" + choice + ". "
						+ (showDetails && correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ quot2html(choices.get(i))
						+ (showDetails && correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>") + "<br/>");
				choice++;
			}
			break;
		case 2: // True/False
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select true or false:</span><UL>");
			buf.append("<LI>" 
					+ (showDetails && correctAnswer.equals("true")?"<B>True</B>":"<FONT COLOR=#888888>True</FONT>") 
					+ "</LI>");
			buf.append("<LI>" 
					+ (showDetails && correctAnswer.equals("false")?"<B>False</B>":"<FONT COLOR=#888888>False</FONT>")
					+ "</LI>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select all of the correct answers:</span><br/>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("&nbsp;" + choice + ". "
						+ (showDetails && correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ quot2html(choices.get(i))
						+ (showDetails && correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>") + "<br/>");
				choice++;
			}
			break;
		case 4: // Fill-in-the-Word
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Enter the correct word or phrase:</span><br/>");
			String[] answers = correctAnswer.split(",");
			buf.append("<span style='border: 1px solid black'>"
					+ (showDetails?"<b>" + quot2html(answers[0]) + "</b>":"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
					+ "</span>");
			if (tag.length() > 0) buf.append("&nbsp;" + tag + "<br/>");
			break;
		case 5: // Numeric Answer
			buf.append(parseString(text) + "<br/>");
			switch (getNumericItemType()) {
			case 0: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the exact value.</span><br/>"); break;
			case 1: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 2: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value to within " + requiredPrecision + "% precision. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 3: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			default:
			}
			buf.append("<span style='border: 1px solid black'>"
					+ (showDetails?"<b>" + getCorrectAnswer() + "</b>":"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
					+ "</span>");
			buf.append("&nbsp;" + parseString(tag) + "<br/>");
			if (showDetails && hint.length()>0) {
				buf.append("<br/>Hint:<br/>" + parseString(hint) + "<br/>");
			}
			if (showDetails && solution.length()>0) {
				buf.append("<br/>Solution:<br/>" + parseString(solution) + "<br/>");
			}
			break;        
		}
		
		buf.append("<br/>");
		if (showWork != null && !showWork.isEmpty()) buf.append("<b>Student work:</b><br/><div style='border-style: solid; border-width: thin; white-space: pre-wrap;'>" + showWork + "</div>");	
		if (studentAnswer==null || studentAnswer.isEmpty()) buf.append("<b>No answer was submitted for this question item.</b><p></p>");
		else {
			buf.append("<b>The answer submitted was: " + studentAnswer + "</b>&nbsp;");
			if (this.isCorrect(studentAnswer)) buf.append("&nbsp;<IMG SRC=/images/checkmark.gif ALT='Check mark' align=bottom>");
			else if (this.agreesToRequiredPrecision(studentAnswer)) buf.append("<IMG SRC=/images/partCredit.png ALT='minus 1 sig figs' align=middle>"
					+ "<br/>Your answer must have exactly " + significantFigures + " significant digits.<br/>If your answer ends in a zero, then it must also have a decimal point to indicate which digits are significant.");
			else buf.append("<IMG SRC=/images/xmark.png ALT='X mark' align=middle>");
			buf.append("<br/><br/>");
		}
		
		if (showDetails) {
			buf.append("<div id='feedback" + this.id + "'>");
			buf.append("<FORM NAME=suggest" + this.id 
					+ " onSubmit=\" return ajaxSubmit('/Feedback?UserRequest=ReportAProblem','" + this.id + "',document.suggest" + this.id + ".Notes.value,document.suggest" + this.id + ".Email.value);\">"
					+ "<INPUT TYPE=BUTTON VALUE='Report a problem with this question' "
					+ "onClick=\"javascript:getElementById('form" + this.id + "').style.display='';this.style.display='none'\" />"
					+ "<div id='form" + this.id + "' style='display: none'>");

			buf.append("<span style=color:red><br/>");
			switch (getQuestionType()) {
			case 1: buf.append("Reminder: The correct answer is shown in bold print above."); break; // MULTIPLE_CHOICE
			case 2: buf.append("Reminder: The correct answer is shown in bold print above."); break; // TRUE_FALSE
			case 3: buf.append("Reminder: The correct answers are shown in bold print above. You must select all of them."); break; // SELECT_MULTIPLE
			case 4: buf.append("Reminder: The correct answer will always form a complete, grammatically correct sentence."); break; // FILL_IN_WORD
			case 5: // NUMERIC
				switch (getNumericItemType()) {
				case 0: buf.append("Reminder: Your answer must have exactly the same value as the correct answer."); break;
				case 1: buf.append("Reminder: Your answer must have exactly the same value as the correct answer and must have " + significantFigures + " significant figures."); break;
				case 2: buf.append("Reminder: Your answer must be within " + requiredPrecision + "% of the correct answer."); break;
				case 3: buf.append("Reminder: Your answer must have " + significantFigures + " significant figures and be within " + requiredPrecision + "% of the correct answer."); break;
				default:
				}
			default:
			}		
			buf.append("</span><br/>");

			buf.append("Your Comment: <INPUT TYPE=TEXT SIZE=80 NAME=Notes /><br/>");
			buf.append("Your Email: <INPUT TYPE=TEXT SIZE=50 PLACEHOLDER=' optional, if you want a response' NAME=Email /><br/>");
			buf.append("<INPUT TYPE=SUBMIT NAME=SubmitButton VALUE='Submit Feedback' /></div></FORM><br/>");
			buf.append("</div>");
		}
		return buf.toString(); 
	}

	String printAnswerToStudents() {
		// use this method to display an example of the question and correct answer, but no detailed solution
		
		StringBuffer buf = new StringBuffer("<a name=" + this.id + "></a>");
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select only the best answer:</span><br/>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("&nbsp;" + choice + ". "
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ quot2html(choices.get(i))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>") + "<br/>");
				choice++;
			}
			break;
		case 2: // True/False
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select true or false:</span><UL>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("true")?"<B>True</B>":"<FONT COLOR=#888888>True</FONT>") 
					+ "</LI>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("false")?"<B>False</B>":"<FONT COLOR=#888888>False</FONT>")
					+ "</LI>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Select all of the correct answers:</span><br/>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("&nbsp;" + choice + ". "
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ quot2html(choices.get(i))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>") + "<br/>");
				choice++;
			}
			break;
		case 4: // Fill-in-the-Word
			buf.append(text + "<br/>");
			buf.append("<span style='color:#EE0000;font-size: small;'>Enter the correct word or phrase:</span><br/>");
			String[] answers = correctAnswer.split(",");
			buf.append("<span style='border: 1px solid black'>"
					+ "<b>" + quot2html(answers[0]) + "</b>"
					+ "</span>");
			if (tag.length() > 0) buf.append("&nbsp;" + tag + "<br/>");
			break;
		case 5: // Numeric Answer
			buf.append(parseString(text) + "<br/>");
			switch (getNumericItemType()) {
			case 0: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the exact value.</span><br/>"); break;
			case 1: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 2: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value to within " + requiredPrecision + "% precision. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			case 3: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
			default:
			}
			buf.append("<span style='border: 1px solid black'>"
					+ "<b>" + getCorrectAnswer() + "</b>"
					+ "</span>");
			buf.append("&nbsp;" + parseString(tag) + "<br/>");
			break;        
		}

		buf.append("<br/>");
		return buf.toString();
	}
	
	public boolean hasSolution() {
		if (solution == null) solution = "";
		return solution.length()>0?true:false;
	}

	public boolean hasHint() {
		if (hint == null) hint = "";
		return hint.length()>0?true:false;
	}

	int getQuestionType() {
		return getQuestionType(this.type);
	}
	
	static int getQuestionType(String type) {
		if (type.equals("MULTIPLE_CHOICE")) return 1;
		if (type.equals("TRUE_FALSE")) return 2;
		if (type.equals("SELECT_MULTIPLE")) return 3;
		if (type.equals("FILL_IN_WORD")) return 4;
		if (type.equals("NUMERIC")) return 5;
		else return 0;
	}

	static String getQuestionType(int type) {
		switch (type) {
			case(1): return "MULTIPLE_CHOICE";
			case(2): return "TRUE_FALSE";
			case(3): return "SELECT_MULTIPLE";
			case(4): return "FILL_IN_WORD";
			case(5): return "NUMERIC";
			default: return "";
		}
	}
	
	public void setQuestionType(int t) { // create a blank form for a new question of type t
		switch (t) {
		case (1): type = "MULTIPLE_CHOICE"; break;
		case (2): type = "TRUE_FALSE"; break;
		case (3): type = "SELECT_MULTIPLE"; break;
		case (4): type = "FILL_IN_WORD"; break;
		case (5): type = "NUMERIC"; break;
		default:  type = "";
		}
	}

	public String edit() {
		StringBuffer buf = new StringBuffer();
		this.validateFields();
		try {
			String[] choiceNames = {"ChoiceAText","ChoiceBText","ChoiceCText","ChoiceDText","ChoiceEText"};
			char choice = 'a';
			switch (this.getQuestionType()) {
			case 1: // Multiple Choice
				buf.append("Question Text:<br/><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ amp2html(text) + "</TEXTAREA><br/>");
				buf.append("<span style='color:#EE0000;font-size: small;'>Select only the best choice:</span><br/>");
				for (int i=0;i<5;i++) { 
					if (i < nChoices) {
						buf.append("<input type=radio name=CorrectAnswer value='" + choice + "'");
						if (correctAnswer.indexOf(choice) >= 0) buf.append(" CHECKED");
						buf.append("/><input size=30 name=" + choiceNames[i] + " value='"); 
						if (choices.size() > i) buf.append(quot2html(amp2html(choices.get(i))));
						buf.append("'/><br/>");
					}
					else buf.append("<input type=radio name=CorrectAnswer value=" + choice + " />"
							+ "<input size=30 name=" + choiceNames[i] + " /><br/>");
					choice++;
				}
				buf.append("<label>Check here to scramble the choices: <input type=checkbox name=ScrambleChoices value=true " + (this.scrambleChoices?"CHECKED":"") + " /></label><br/>");
				break;
			case 2: // True/False
				buf.append("Question Text:<br/><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ amp2html(text) + "</TEXTAREA><br/>");
				buf.append("<span style='color:#EE0000;font-size: small;'>Select true or false:</span><br/>");
				buf.append("<input type=radio name=CorrectAnswer value='true'");
					if (correctAnswer.equals("true")) buf.append(" CHECKED");
				buf.append("/> True<br/>");
				buf.append("<input type=radio name=CorrectAnswer value='false'");
				if (correctAnswer.equals("false")) buf.append(" CHECKED");
				buf.append("/> False<br/>");
				break;
			case 3: // Select Multiple
				buf.append("Question Text:<br/><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ amp2html(text) + "</TEXTAREA><br/>");
				buf.append("<span style='color:#EE0000;font-size: small;'>Select all of the correct answers:</span><br/>");
				for (int i=0;i<5;i++){
					if (i < nChoices) {
						buf.append("<input type=checkbox name=CorrectAnswer value='" + choice + "'");
						if (correctAnswer.indexOf(choice) >= 0) buf.append(" CHECKED");
						buf.append("/><input size=30 name=" + choiceNames[i] + " value='"); 
						if (choices.size() > i) buf.append(quot2html(amp2html(choices.get(i))));
						buf.append("'/><br/>");
					}
					else buf.append("<input type=checkbox name=CorrectAnswer value=" + choice + " />"
							+ "<input size=30 name=" + choiceNames[i] + " /><br/>");
					choice++;
				}
				buf.append("<label>Check here to scramble the choices: <input type=checkbox name=ScrambleChoices value=true " + (this.scrambleChoices?"CHECKED":"") + " /></label><br/>");
				break;
			case 4: // Fill-in-the-Word
				buf.append("Question Text:<br/><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ amp2html(text) + "</TEXTAREA><br/>");
				buf.append("<span style='color:#EE0000;font-size: small;'>Enter the correct word or phrase.<br/>"
						+ "Multiple correct answers can be entered as a comma-separated list.</span><br/>");
				buf.append("<input type=text name=CorrectAnswer value=\"" 
						+ quot2html(amp2html(correctAnswer)) + "\"'/><br/>");
				buf.append("<TEXTAREA name=QuestionTag rows=5 cols=60 wrap=soft>" 
						+ amp2html(tag) + "</TEXTAREA><br/>");
				break;
			case 5: // Numeric Answer
				buf.append("Question Text:<br/><TEXTAREA name=QuestionText rows=5 cols=60 wrap=soft>" 
						+ amp2html(text) + "</TEXTAREA><br/>");
				buf.append("<FONT SIZE=-2>Significant figures: <input size=5 name=SignificantFigures value='" + significantFigures + "'/> Required precision: <input size=5 name=RequiredPrecision value='" + requiredPrecision + "'/> (set to zero to require exact answer)</FONT><br/>");
				switch (getNumericItemType()) {
				case 0: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the exact value.</span><br/>"); break;
				case 1: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
				case 2: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value to within " + requiredPrecision + "% precision. For scientific notation use a format like 4.29E-15</span><br/>"); break;
				case 3: buf.append("<span style='color:#EE0000;font-size: small;'>Enter the value with the appropriate number of significant figures. For scientific notation use a format like 4.29E-15</span><br/>"); break;
				default:
				}
				buf.append("Correct answer:");
				buf.append("<INPUT TYPE=TEXT NAME=CorrectAnswer VALUE='" + correctAnswer + "'/> ");
				buf.append(" Units:<INPUT TYPE=TEXT NAME=QuestionTag SIZE=8 VALUE='" 
						+ quot2html(amp2html(tag)) + "'/><br/>");
				buf.append("Parameters:<input name=ParameterString value='" 
						+ parameterString + "'/><FONT SIZE=-2><a href=# onClick=\"javascript:document.getElementById('detail1').innerHTML="
						+ "'You may embed up to 4 parameters (a b c d) in a question using a parameter string like<br/>"
						+ "a 111:434 b 7:39<br/>"
						+ "This will randomly select integers for variables a and b from the specified ranges.<br/>"
						+ "Use these in math expressions with the pound sign delimeter (#) to create randomized data.<br/>"
						+ "Example: Compute the mass of sodium in #a# mL of aqueous #b/10# M NaCl solution.<br/>"
						+ "Correct answer: #22.9898*a/1000*b/10# g<p></p>"
						+ "You can also display fractions in vertical format using encoding like (|numerator|denominator|)<br/><br/>'\";>What's This?</a></FONT>");
				buf.append("<div id=detail1></div>");
				buf.append("Hint:<br/><TEXTAREA NAME=Hint ROWS=3 COLS=60 WRAP=SOFT>"
						+ amp2html(hint) + "</TEXTAREA><br/>");
				buf.append("Solution:<br/><TEXTAREA NAME=Solution ROWS=10 COLS=60 WRAP=SOFT>" 
						+ amp2html(solution) + "</TEXTAREA><br/>");
				break;
			}
		}
		catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	boolean hasNoCorrectAnswer() {
		return this.correctAnswer == null || this.correctAnswer.isEmpty();
	}
	
	boolean hasACorrectAnswer() {
		return !hasNoCorrectAnswer();
	}
	
	boolean isCorrect(String studentAnswer){
		if (studentAnswer == null || studentAnswer.isEmpty() || hasNoCorrectAnswer()) return false;
		switch (getQuestionType()) {
		case 4:  // Fill-in-the-word
			Collator compare = Collator.getInstance();
			compare.setStrength(Collator.PRIMARY);
			studentAnswer = studentAnswer.replaceAll("\\W", "");
			String[] correctAnswers = correctAnswer.split(","); // break comma-separated list into array
			for (int i=0;i<correctAnswers.length;i++) {
				correctAnswers[i] = correctAnswers[i].replaceAll("\\W","");
				if (compare.equals(studentAnswer,correctAnswers[i])) return true;
				else if (closeEnough(studentAnswer.toLowerCase(),correctAnswers[i].toLowerCase())) return true;
			}
			return false;
		case 5: // Numeric Answer
			return hasCorrectSigFigs(studentAnswer) && agreesToRequiredPrecision(studentAnswer);
		default:  // exact match to non-numeric answer (MULTIPLE_CHOICE, TRUE_FALSE, SELECT_MULTIPLE)
			return correctAnswer.equals(studentAnswer);
		}
	}
	
	boolean closeEnough(String studentAnswer,String correctAnswer) {
		if (correctAnswer.length() < 4) return false;  			// exact answer needed for 3-char answers
		int maxEditDistance = correctAnswer.length()<6?1:2;		// 1 error allowed for 4,5-char answers; otherwise 2 errors allowed

		if (Math.abs(correctAnswer.length()-studentAnswer.length())>maxEditDistance) return false;   // trivial estimate of min edit distance

		if (editDist(studentAnswer,correctAnswer,studentAnswer.length(),correctAnswer.length(),0,maxEditDistance) > maxEditDistance) return false;

		return true;
	}
	
	static int editDist(String str1, String str2, int m, int n, int d, int maxEditDistance) {
		/* 
		 * Modified Levenshtein algorithm for computing the edit distance between 2 strings of length m and n, and comparing it to a
		 * maxEditDistance, keeping in mind that insertions or deletions that increase the distance from the m=n diagonal incur extra cost.
		 */
		if (m == 0) return n; 	  
		if (n == 0) return m; 

		if (str1.charAt(m - 1) == str2.charAt(n - 1)) return editDist(str1, str2, m - 1, n - 1, d, maxEditDistance); 
		else if (d >= maxEditDistance) return d+1;
		else return 1 + min(d+1+Math.abs(n-1-m)>maxEditDistance?maxEditDistance:editDist(str1, str2, m, n-1, d+1, maxEditDistance),  // Insert 
							d+1+Math.abs(n-m+1)>maxEditDistance?maxEditDistance:editDist(str1, str2, m-1, n, d+1, maxEditDistance),  // Remove 
							d+1+Math.abs(n-m)>maxEditDistance?maxEditDistance:editDist(str1, str2, m-1, n-1, d+1, maxEditDistance)); // Replace 
	}

	static int min(int x, int y, int z) { /*This code is contributed by Rajat Mishra*/
		if (x <= y && x <= z) return x; 
		if (y <= x && y <= z) return y; 
		else return z; 
	} 
/*	  
	boolean hasCorrectSigFigs(String studentAnswer) {
		// This method check the value to ensure that it has a number of significant figures that is consistent with Question.significantFigures
		// Actually, it only ensures that value does not have more sig figs, because there may be an ambiguity in the number of sig figs if the 
		// last significant digit in either the question item or the studentAnswer is a zero.
		if (significantFigures==0) return true; // correct sig figs not required for this question
		try {
			studentAnswer = studentAnswer.replaceAll(",", "").replaceAll("\\s", "").toUpperCase();  // removes comma separators and whitespace from numbers, turns e to E
			Double proposedValue = Double.valueOf(String.format("%."+(significantFigures+6)+"G", Double.valueOf(studentAnswer)));  // high-resolution representation of value
			Double roundedValue = Double.valueOf(String.format("%."+significantFigures+"G", Double.valueOf(studentAnswer)));       // rounded to proper number of significant figures
			if (Double.compare(roundedValue,proposedValue)==0) return true;
			else return false;
		} catch (Exception e) {  // unexpected error
			return false;
		}
	}
*/	
	boolean hasCorrectSigFigs(String studentAnswer) {
		if (significantFigures==0) return true;  // no sig figs required
		
		studentAnswer = studentAnswer.replaceAll(",", "").replaceAll("\\s", "");  // removes comma separators and whitespace from numbers
		
		int exponentPosition = studentAnswer.toUpperCase().indexOf("E");  		// turns "e" to "E"
		String mantissa = exponentPosition>=0?studentAnswer.substring(0,exponentPosition):studentAnswer;
		
		// check to see if the value has a trailing zero before the decimal place
		// this check has been disabled to forgive lack of a trailing decimal
		//if (mantissa.indexOf(".")==-1 && mantissa.endsWith("0")) return false;
		
		// strip leading (non-significant) zeros and decimals
		while (mantissa.startsWith("0") || mantissa.startsWith(".")) mantissa = mantissa.substring(1);
		
		// remove all instances of minus sign and decimal point:
		mantissa = mantissa.replace("-","").replace(".","");
			
		// see if number of remaining digits matches this.significantFigures
		if (mantissa.length()==this.significantFigures) return true;
		
		return false;
	}
	
	boolean agreesToRequiredPrecision(String studentAnswer) {
		// This method is used for numeric questions to determine if the student's response agrees with the correct answer to within the required precision
		if (!"NUMERIC".equals(type) || studentAnswer == null || studentAnswer.isEmpty() || hasNoCorrectAnswer()) return false;
		try {
			studentAnswer = studentAnswer.replaceAll(",", "").replaceAll("\\s", "").toUpperCase();  // removes comma separators and whitespace from numbers, turns e to E
			double dStudentAnswer = Double.parseDouble(parseString(studentAnswer,0));
			double dCorrectAnswer = Double.parseDouble(parseString(correctAnswer));
			if (requiredPrecision==0.) return Double.compare(dStudentAnswer,dCorrectAnswer)==0.; // exact match required
			if (dCorrectAnswer==0.) return dStudentAnswer==0.;  // exact match is always required if the correct answer is zero; avoids divide-by-zero in next line
			else return (Math.abs((dStudentAnswer-dCorrectAnswer)/dCorrectAnswer)*100 <= requiredPrecision?true:false);  // checks for agreement to required precision
		} catch (Exception e) {
			return false;
		}
	}
	
	public Question clone() {
		try {
			return (Question) super.clone();
		} catch (Exception e) {
			return null;
		}
	}
	
	// The following methods are from the original CharHider class to guard against
	// inadvertent mistakes in interpreting user input, especially in Question items.

	static String quot2html(String oldString) {
		if (oldString == null) return "";
		// recursive method replaces single quotes with &#39; for HTML pages
		int i = oldString.indexOf('\'',0);
		return i<0?oldString:quot2html(new StringBuffer(oldString).replace(i,i+1,"&#39;").toString(),i);
	}

	static String quot2html(String oldString,int fromIndex) {
		// recursive method replaces single quotes with &#39; for HTML pages
		int i = oldString.indexOf('\'',fromIndex);
		return i<0?oldString:quot2html(new StringBuffer(oldString).replace(i,i+1,"&#39;").toString(),i);
	}

	static String amp2html(String oldString) {
		if (oldString == null) return "";
		// recursive method replaces ampersands with &amp; for preloading Greek/special characters in text fields in HTML forms
		int i = oldString.indexOf('&',0);
		//		    return i<0?oldString:new StringBuffer(oldString).replace(i,i+1,"&amp;").toString();
		return i<0?oldString:amp2html(new StringBuffer(oldString).replace(i,i+1,"&amp;").toString(),i+1);
	}

	static String amp2html(String oldString,int fromIndex) {
		// recursive method replaces ampersands with &amp; for preloading Greek/special characters in text fields in HTML forms
		int i = oldString.indexOf('&',fromIndex);
		return i<0?oldString:amp2html(new StringBuffer(oldString).replace(i,i+1,"&amp;").toString(),i+1);
	}
	
	String parseFractions(String expression) {
		return parseFractions(expression,0);
	}
	
	String parseFractions(String expression, int startIndex) {
		// This method uses parentheses and the pipe character (|) to identify numerator and denominator of a fraction
		// to be displayed in a vertical format. The encoding is like (| numerator | denominator |)
		final String num = "<span style='display: inline-block;vertical-align: middle;font-size: smaller'><div style='text-align: center;border-bottom: 1px solid black;'>";
		final String pip = "</div><div style='text-align: center;'>";
		final String den = "</div></span>";
		
		int i = expression.indexOf("(|",startIndex);  	// marks the first start of a numerator
		if (i<0) return expression;						// quick return if no fractions found
		
		int i2 = expression.indexOf("(|",i+2);			// marks the second start of a numerator
		int k = expression.indexOf("|)",i+2);			// marks the end of a denominator
		if (i2>0 && i2<k) {								// second start is before first end; start recursion
			expression = parseFractions(expression,i2);
			k = expression.indexOf("|)",i+2);			// recalculate the end due to substitutions made
		}
		if (k<0) return expression; 					// there must be and end-of-fraction marker to proceed
		
		int j = expression.indexOf("|",i+2);			// marks separator between numerator and denominator
		
		// Replace the markers with CSS style tags:
		if (j>0 && j<k) {
			expression = expression.substring(0,i) + num + expression.substring(i+2,j) + pip + expression.substring(j+1,k) + den + expression.substring(k+2);
			k = k - 5 + num.length() + pip.length() + den.length();
		}
		
		// Test to see if there is another fraction at this level:
		return parseFractions(expression,k+1);
	}
}
