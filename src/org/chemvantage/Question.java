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
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Cache @Entity
public class Question implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index	long topicId;
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
			// Note: the parameters array formerly had the attribute @Transient javax.persistence.Transient
			int[] parameters = {0,0,0,0};
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
	
	String print() {
		return print("");
	}
	
	String print(String studentAnswer) {
		StringBuffer buf = new StringBuffer();
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select only the best answer:</FONT><br>");
			buf.append("<UL>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("<label><input type=radio name=" + this.id + " value=" + choice + (studentAnswer.indexOf(choice)>=0?" CHECKED>":">") + choices.get(i) + "</label><br>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 2: // True/False
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select true or false:</FONT><br>");
			buf.append("<UL>");
			buf.append("<label><input type=radio name=" + this.id + " value='true'" + (studentAnswer.equals("true")?" CHECKED>":">") + " True</label><br>");
			buf.append("<label><input type=radio name=" + this.id + " value='false'" + (studentAnswer.equals("false")?" CHECKED>":">") + " False</label><br>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select all of the correct answers:</FONT><br>");
			buf.append("<UL>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("<label><input type=checkbox name=" + this.id + " value=" + choice + (studentAnswer.indexOf(choice)>=0?" CHECKED>":">") + choices.get(i) + "</label><br>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 4: // Fill-in-the-Word
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase:</FONT><br>");
			buf.append("<input id=" + this.id + " type=text name=" + this.id + " value='" + CharHider.quot2html(studentAnswer) + "'>");
			buf.append(tag + "<br>");
			break;
		case 5: // Numeric Answer
			buf.append(parseString(text) + "<br />");
			switch (getNumericItemType()) {
			case 0: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the exact numerical value.</FONT><br>"); break;
			case 1: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
			case 2: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " + requiredPrecision + "% precision. Express scientific notation like 4.29E-15</FONT><br>"); break;
			case 3: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
			default:
			}			
			buf.append("<input type=text name=" + this.id + " value='" + studentAnswer + "'>");
			buf.append(parseString(tag) + "<br />");
			break;        
		}
		return buf.toString();
	}

	String getHint() {
		return parseString(hint);
	}
	
	String printAll() {
		// use this method to display an example of the question, correct answer and solution
		StringBuffer buf = new StringBuffer();
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select only the best answer:</FONT><UL>");
			for (int i = 0; i < nChoices && i < choices.size(); i++) {
				buf.append("<LI>" 
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ CharHider.quot2html(choices.get(i))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>")
						+ "</LI>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 2: // True/False
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select true or false:</FONT><UL>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("true")?"<B>True</B>":"<FONT COLOR=#888888>True</FONT>") 
					+ "</LI>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("false")?"<B>False</B>":"<FONT COLOR=#888888>False</FONT>") 
					+ "</LI>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select all of the correct answers:</FONT><UL>");
			for (int i = 0; i < nChoices && i < choices.size(); i++) {
				buf.append("<LI>"
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ CharHider.quot2html(choices.get(i))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>")
						+ "</LI>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 4: // Fill-in-the-Word
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase:</FONT><br>");
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + CharHider.quot2html(correctAnswer) + "</b>"
					+ "</TD></TR></TABLE>");
			buf.append(tag + "<p><p>");
			break;
		case 5: // Numeric Answer
			buf.append(parseString(text) + "<br />");
			switch (getNumericItemType()) {
			case 0: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the exact numerical value.</FONT><br>"); break;
			case 1: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
			case 2: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " + requiredPrecision + "% precision. Express scientific notation like 4.29E-15</FONT><br>"); break;
			case 3: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
			default:
			}
			buf.append("<TABLE><TR><TD><TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + getCorrectAnswer() + "</b>"
					+ "</TD></TR></TABLE></TD><TD>");
			buf.append(parseString(tag) + "</TD></TR></TABLE>");
			if (hint.length()>0) {
				buf.append("Hint:<br>" + parseString(hint) + "<p>");
			}
			if (solution.length()>0) {
				buf.append("Solution:<br>" + parseString(solution));
			}
			buf.append("<p>");
			break;        
		}
		return buf.toString();
	}

	String printAllToStudents(String studentAnswer) {
		return printAllToStudents(studentAnswer,true);
	}
	
	String printAllToStudents(String studentAnswer,boolean showDetails) {
		// use this method to display an example of the question, correct answer and solution
		// this differs from printAll() because only the first of several 
		// correct fill-in-word answers is presented
		StringBuffer buf = new StringBuffer("<a name=" + this.id + ">");
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select only the best answer:</FONT><br>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("&nbsp;" + choice + ". "
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ CharHider.quot2html(choices.get(i))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>") + "<br>");
				choice++;
			}
			break;
		case 2: // True/False
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select true or false:</FONT><UL>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("true")?"<B>True</B>":"<FONT COLOR=#888888>True</FONT>") 
					+ "</LI>");
			buf.append("<LI>" 
					+ (correctAnswer.equals("false")?"<B>False</B>":"<FONT COLOR=#888888>False</FONT>")
					+ "</LI>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select all of the correct answers:</FONT><BR>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("&nbsp;" + choice + ". "
						+ (correctAnswer.indexOf(choice)>=0?"<B>":"<FONT COLOR=#888888>")
						+ CharHider.quot2html(choices.get(i))
						+ (correctAnswer.indexOf(choice)>=0?"</B>":"</FONT>") + "<br>");
				choice++;
			}
			break;
		case 4: // Fill-in-the-Word
			buf.append(text + "<br />");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase:</FONT><br>");
			String[] answers = correctAnswer.split(",");
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + CharHider.quot2html(answers[0]) + "</b>"
					+ "</TD></TR></TABLE>");
			if (tag.length() > 0) buf.append(tag + "<br />");
			break;
		case 5: // Numeric Answer
			buf.append(parseString(text) + "<br />");
			switch (getNumericItemType()) {
			case 0: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the exact numerical value.</FONT><br>"); break;
			case 1: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
			case 2: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " + requiredPrecision + "% precision. Express scientific notation like 4.29E-15</FONT><br>"); break;
			case 3: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
			default:
			}
			buf.append("<TABLE><TR><TD><TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + getCorrectAnswer() + "</b>"
					+ "</TD></TR></TABLE></TD><TD>");
			buf.append(parseString(tag) + "</TD></TR></TABLE>");
			if (hint.length()>0) {
				buf.append("Hint:<br>" + parseString(hint) + "<p>");
			}
			if (showDetails && solution.length()>0) {
				buf.append("Solution:<br>" + parseString(solution));
				buf.append("<p>");
			}
			break;        
		}

		if (studentAnswer.length() > 0) buf.append("Your answer was: " + studentAnswer + "<br>");

		buf.append("<div id='feedback" + this.id + "'>"
				+ "<FORM NAME=suggest" + this.id 
				+ " onSubmit=\" return ajaxSubmit('Feedback?UserRequest=ReportAProblem','" + this.id + "',document.suggest" + this.id + ".Notes.value);\">"
				+ "<INPUT TYPE=BUTTON VALUE='Report a problem with this question' "
				+ "onClick=javascript:getElementById('form" + this.id + "').style.display='';this.style.display='none'>"
				+ "<div id='form" + this.id + "' style='display: none'><div style=color:red>");
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
		buf.append("</div>Comment:<INPUT TYPE=TEXT SIZE=80 NAME=Notes><INPUT TYPE=SUBMIT NAME=SubmitButton VALUE=Send></div></FORM></div>");

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
				buf.append("Question Text:<br><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ CharHider.amp2html(text) + "</TEXTAREA><br>");
				buf.append("<FONT SIZE=-2 COLOR=FF0000>Select only the best choice:</FONT><br>");
				buf.append("<UL>");
				for (int i=0;i<5;i++) { 
					if (i < nChoices) {
						buf.append("<input type=radio name=CorrectAnswer value=" + choice + "");
						if (correctAnswer.indexOf(choice) >= 0) buf.append(" CHECKED");
						buf.append("><input size=30 name=" + choiceNames[i] + " value='"); 
						if (choices.size() > i) buf.append(CharHider.quot2html(CharHider.amp2html(choices.get(i))));
						buf.append("'><br>");
					}
					else buf.append("<input type=radio name=CorrectAnswer value='" + choice + "'>"
							+ "<input size=30 name=" + choiceNames[i] + "><br>");
					choice++;
				}
				buf.append("</UL>");
				break;
			case 2: // True/False
				buf.append("Question Text:<br><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ CharHider.amp2html(text) + "</TEXTAREA><br>");
				buf.append("<FONT SIZE=-2 COLOR=FF0000>Select true or false:</FONT><br>");
				buf.append("<input type=radio name=CorrectAnswer value='true'");
					if (correctAnswer.equals("true")) buf.append(" CHECKED");
				buf.append("> True<br>");
				buf.append("<input type=radio name=CorrectAnswer value='false'");
				if (correctAnswer.equals("false")) buf.append(" CHECKED");
				buf.append("> False<br>");
				break;
			case 3: // Select Multiple
				buf.append("Question Text:<br><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ CharHider.amp2html(text) + "</TEXTAREA><br>");
				buf.append("<FONT SIZE=-2 COLOR=FF0000>Select all of the correct answers:</FONT><br>");
				buf.append("<UL>");
				for (int i=0;i<5;i++){
					if (i < nChoices) {
						buf.append("<input type=checkbox name=CorrectAnswer value=" + choice);
						if (correctAnswer.indexOf(choice) >= 0) buf.append(" CHECKED");
						buf.append("><input size=30 name=" + choiceNames[i] + " value='"); 
						if (choices.size() > i) buf.append(CharHider.quot2html(CharHider.amp2html(choices.get(i))));
						buf.append("'><br>");
					}
					else buf.append("<input type=checkbox name=CorrectAnswer value=" + choice + ">"
							+ "<input size=30 name=" + choiceNames[i] + "><br>");
					choice++;
				}
				buf.append("</UL>");
				break;
			case 4: // Fill-in-the-Word
				buf.append("Question Text:<br><TEXTAREA name=QuestionText rows=5 cols=50 wrap=soft>" 
						+ CharHider.amp2html(text) + "</TEXTAREA><br>");
				buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase.<br>"
						+ "Multiple correct answers can be entered as a comma-separated list.</FONT><br>");
				buf.append("<input type=text name=CorrectAnswer value=\"" 
						+ CharHider.quot2html(CharHider.amp2html(correctAnswer)) + "\"'><br>");
				buf.append("<TEXTAREA name=QuestionTag rows=5 cols=60 wrap=soft>" 
						+ CharHider.amp2html(tag) + "</TEXTAREA><br>");
				break;
			case 5: // Numeric Answer
				buf.append("Question Text:<br><TEXTAREA name=QuestionText rows=5 cols=60 wrap=soft>" 
						+ CharHider.amp2html(text) + "</TEXTAREA><br>");
				buf.append("<FONT SIZE=-2>Significant figures: <input size=5 name=SignificantFigures value='" + significantFigures + "'> Required precision: <input size=5 name=RequiredPrecision value='" + requiredPrecision + "'> (set to zero to require exact answer)</FONT><br/>");
				switch (getNumericItemType()) {
				case 0: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the exact numerical value.</FONT><br>"); break;
				case 1: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
				case 2: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " + requiredPrecision + "% precision. Express scientific notation like 4.29E-15</FONT><br>"); break;
				case 3: buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value with the appropriate number of significant figures. Express scientific notation like " + String.format("%."+significantFigures+"G",4.2873648E-15) + "</FONT><br>"); break;
				default:
				}
				buf.append("Correct answer:");
				buf.append("<INPUT TYPE=TEXT NAME=CorrectAnswer VALUE='" + correctAnswer + "'> ");
				buf.append(" Units:<INPUT TYPE=TEXT NAME=QuestionTag SIZE=8 VALUE='" 
						+ CharHider.quot2html(CharHider.amp2html(tag)) + "'><br>");
				buf.append("Parameters:<input name=ParameterString value='" 
						+ parameterString + "'><FONT SIZE=-2><a href=# onClick=\"javascript:document.getElementById('detail1').innerHTML="
						+ "'You may embed up to 4 parameters (a b c d) in a question using a parameter string like<br>"
						+ "a 111:434 b 7:39<br>"
						+ "This will randomly select integers for variables a and b from the specified ranges.<br>"
						+ "Use these in math expressions with the pound sign delimeter (#) to create randomized data.<br>"
						+ "Example: Compute the mass of sodium in #a# mL of aqueous #b/10# M NaCl solution.<br>"
						+ "Correct answer: #22.9898*a/1000*b/10# g<p>'\";>What's This?</a></FONT>");
				buf.append("<div id=detail1></div>");
				buf.append("Hint:<br><TEXTAREA NAME=Hint ROWS=3 COLS=60 WRAP=SOFT>"
						+ CharHider.amp2html(hint) + "</TEXTAREA><br>");
				buf.append("Solution:<br><TEXTAREA NAME=Solution ROWS=10 COLS=60 WRAP=SOFT>" 
						+ CharHider.amp2html(solution) + "</TEXTAREA><br>");
				break;
			}
		}
		catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	boolean isCorrect(String studentAnswer){
		if (studentAnswer == null || studentAnswer.isEmpty()) return false;
		switch (getQuestionType()) {
		case 4:  // Fill-in-the-word
			Collator compare = Collator.getInstance();
			compare.setStrength(Collator.PRIMARY);
			studentAnswer = studentAnswer.replaceAll("\\W", "");
			String[] correctAnswers = correctAnswer.split(","); // break comma-separated list into array
			for (int i=0;i<correctAnswers.length;i++) {
				if (compare.equals(studentAnswer,correctAnswers[i].replaceAll("\\W",""))) return true;
			}
			return false;
		case 5: // Numeric Answer
			return hasCorrectSigFigs(studentAnswer) && agreesToRequiredPrecision(studentAnswer);
		default:  // exact match to non-numeric answer (MULTIPLE_CHOICE, TRUE_FALSE, SELECT_MULTIPLE)
			return correctAnswer.equals(studentAnswer);
		}
	}
	
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
	
	boolean agreesToRequiredPrecision(String studentAnswer) {
		// This method is used for numeric questions to determine if the student's response agrees with the correct answer to within the required precision
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
		Question q = new Question(this.getQuestionType());
		q.assignmentType = this.assignmentType;
		q.topicId = this.topicId;
		q.text = this.text;
		q.nChoices = this.nChoices;
		q.choices = this.choices;
		q.requiredPrecision = this.requiredPrecision;
		q.significantFigures = this.significantFigures;
		q.correctAnswer = this.correctAnswer;
		q.tag = this.tag;
		q.pointValue = this.pointValue;
		q.parameterString = this.parameterString;
		q.hint = this.hint;
		q.solution = this.solution;
		q.authorId = this.authorId;
		q.contributorId = this.contributorId;
		q.editorId = this.editorId;
		q.notes = this.notes;
		return q;
	}
}
