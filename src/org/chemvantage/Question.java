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

import javax.persistence.Id;
import javax.persistence.Transient;

import com.bestcode.mathparser.IMathParser;
import com.bestcode.mathparser.MathParserFactory;
import com.googlecode.objectify.annotation.Cached;

@Cached
public class Question implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	long topicId;
	String assignmentType;
	String text;
	String type;
	int nChoices=0;
	List<String> choices = new ArrayList<String>();
	double requiredPrecision=2;
	String correctAnswer;
	String tag;
	int pointValue=1;
	String parameterString;
	String hint;
	String solution;
	String authorId;
	String contributorId;
	String editorId;
	String notes;
	@Transient int[] parameters = {0,0,0,0};
	boolean requiresParser = false;
	boolean isActive = false;
	
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
		case (5): this.type = "NUMERIC"; break;
		default:  this.type = null;
		}
		this.correctAnswer = "";
		this.parameterString = "";
		this.requiresParser = false;
		this.pointValue = 1;
		this.requiredPrecision = 2.0;
		this.isActive = false;
	}

	Question (long topicId,String text,String type,int nChoices,List<String> choices,
			double requiredPrecision,String correctAnswer,String tag,int pointValue,String parameterString,
			String hint,String solution,String authorId,String contributorId,String editorId,String notes) {
		this.topicId = topicId;
		this.text = text;
		this.type = type;
		this.nChoices = nChoices;
		this.choices = choices;
		this.requiredPrecision = requiredPrecision;
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
		this.requiresParser = this.parameterString.length()>0?true:false;
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
	
	public void setParameters() {
		if (this.requiresParser) setParameters(-1); // set parameters with a random seed based on the current time
	}

	public void setParameters(long seed) {
		if (!this.requiresParser) return;     // bulletproofing
		if (this.parameterString==null || this.parameterString.length()==0) {
			this.parameterString = "";
			this.requiresParser = false;
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

	public String parseString(String raw) {  
		// this section uses a fully licensed version of the Jbc Math Parser
		// from bestcode.com (license purchased by C. Wight on Nov 18, 2007)

		// The next line was deleted to allow for student answers in the form of equations, even for nonparameterized questions
		//if (!this.requiresParser) return raw==null?"":raw;  // no parsing required for this question
		
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
		int i = 0;
		while (i < pieces.length) {
			try {
				parser.setExpression(pieces[i]);
				double result = parser.getValue();
				DecimalFormat df = new DecimalFormat();
				if ((Math.abs(result) < 100) && (result - Math.floor(result) == 0)) { // result is small int
					df.applyPattern("0");
				}
				else if ((Math.abs(result) < 1.0E5) && (Math.abs(result) > 1.0E-2)) { // use decimal number
					df.applyPattern("0.0#####");
				}
				else df.applyPattern("0.####E0"); // use scientific notation
				buf.append(df.format(result));
			}
			catch (Exception e) {
				buf.append(pieces[i]);
			}
			finally {
				i++;
			}
		}
		return buf.toString();
	}
/* This method was deprecated in favor of the printPremium method that shows extra features
	String print() {
		StringBuffer buf = new StringBuffer();
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select only the best answer:</FONT><br>");
			buf.append("<UL>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("<input type=radio name=" + this.id + " value=" + choice + ">" + choices.get(i) + "<br>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 2: // True/False
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select true or false:</FONT><br>");
			buf.append("<UL>");
			buf.append("<input type=radio name=" + this.id + " value='true'> True<br>");
			buf.append("<input type=radio name=" + this.id + " value='false'> False<br>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select all of the correct answers:</FONT><br>");
			buf.append("<UL>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("<input type=checkbox name=" + this.id + " value=" + choice + ">" + choices.get(i) + "<br>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 4: // Fill-in-the-Word
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase:</FONT><br>");
			buf.append("<input id=" + this.id + " type=text name=" + this.id + ">");
			buf.append("<b>" + tag + "</b><br>");
			break;
		case 5: // Numeric Answer
			buf.append("<b>" + parseString(text) + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " 
					+ requiredPrecision + "% precision. Express scientific notation like 4.294E-15</FONT><br>");
			buf.append("<input type=text name=" + this.id + ">");
			buf.append("<b>" + parseString(tag) + "</b><br>");
			break;        
		}
		return buf.toString();
	}
	*/
	String print() {  // this method includes functionality to access spell checking and Google searches
		StringBuffer buf = new StringBuffer();
		char choice = 'a';
		String q = new String(text + " " + tag).replaceAll("\\<[^>]*>","").trim();
		String searchURL = "http://www.google.com/search?q=" + q.replace(' ','+');
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select only the best answer:</FONT><br>");
			buf.append("<UL>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("<input type=radio name=" + this.id + " value=" + choice + ">" + choices.get(i) + "<br>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 2: // True/False
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select true or false:</FONT><br>");
			buf.append("<UL>");
			buf.append("<input type=radio name=" + this.id + " value='true'> True<br>");
			buf.append("<input type=radio name=" + this.id + " value='false'> False<br>");
			buf.append("</UL>");
			break;
		case 3: // Select Multiple
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Select all of the correct answers:</FONT><br>");
			buf.append("<UL>");
			for (int i = 0; i < nChoices; i++) {
				buf.append("<input type=checkbox name=" + this.id + " value=" + choice + ">" + choices.get(i) + "<br>");
				choice++;
			}
			buf.append("</UL>");
			break;
		case 4: // Fill-in-the-Word
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase:</FONT><br>");
			buf.append("<div id=status" + this.id + " style='font-size:10px'><button type=button "
					+ "onClick=\"javascript: document.getElementById('status" + this.id + "')"
					+ ".innerHTML='checking...';ajaxSpellCheck(" + this.id + ");\">Check Spelling</button>"
					+ "<a target=_blank href=" + searchURL + ">Google Search</a></div>");
			buf.append("<input id=" + this.id + " type=text name=" + this.id + ">");
			buf.append("<b>" + tag + "</b><br>");
			break;
		case 5: // Numeric Answer
			buf.append("<b>" + parseString(text) + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " 
					+ requiredPrecision + "% precision. Express scientific notation like 4.294E-15</FONT><br>");
			buf.append("<input type=text name=" + this.id + ">");
			buf.append("<b>" + parseString(tag) + "</b><br>");
			break;        
		}
		return buf.toString();
	}

	String printAll() {
		// use this method to display an example of the question, correct answer and solution
		StringBuffer buf = new StringBuffer();
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append("<b>" + text + "</b><br>");
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
			buf.append("<b>" + text + "</b><br>");
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
			buf.append("<b>" + text + "</b><br>");
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
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase:</FONT><br>");
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + CharHider.quot2html(correctAnswer) + "</b>"
					+ "</TD></TR></TABLE>");
			buf.append("<b>" + tag + "</b><p><p>");
			break;
		case 5: // Numeric Answer
			buf.append("<b>" + parseString(text) + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " 
					+ requiredPrecision + "% precision. Express scientific notation like 4.294E-15</FONT><br>");
			buf.append("<TABLE><TR><TD><TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + getCorrectAnswer() + "</b>"
					+ "</TD></TR></TABLE></TD><TD>");
			buf.append("<b>" + parseString(tag) + "</b></TD></TR></TABLE>");
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

	public String printAllToStudents(String studentAnswer) {
		// use this method to display an example of the question, correct answer and solution
		// this differs from printAll() because only the first of several 
		// correct fill-in-word answers is presented
		StringBuffer buf = new StringBuffer("<a name=" + this.id + ">");
		char choice = 'a';
		switch (getQuestionType()) {
		case 1: // Multiple Choice
			buf.append("<b>" + text + "</b><br>");
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
			buf.append("<b>" + text + "</b><br>");
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
			buf.append("<b>" + text + "</b><br>");
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
			buf.append("<b>" + text + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct word or phrase:</FONT><br>");
			String[] answers = correctAnswer.split(",");
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + CharHider.quot2html(answers[0]) + "</b>"
					+ "</TD></TR></TABLE>");
			if (tag.length() > 0) buf.append("<b>" + tag + "</b><br>");
			break;
		case 5: // Numeric Answer
			buf.append("<b>" + parseString(text) + "</b><br>");
			buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " 
					+ requiredPrecision + "% precision. Express scientific notation like 4.294E-15</FONT><br>");
			buf.append("<TABLE><TR><TD><TABLE BORDER=1 CELLSPACING=0><TR><TD>"
					+ "<b>" + getCorrectAnswer() + "</b>"
					+ "</TD></TR></TABLE></TD><TD>");
			buf.append("<b>" + parseString(tag) + "</b></TD></TR></TABLE>");
			if (hint.length()>0) {
				buf.append("Hint:<br>" + parseString(hint) + "<p>");
			}
			if (solution.length()>0) {
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
				+ "<div id='form" + this.id + "' style='display: none'><div style=color:red>"
				+ (getQuestionType()==3?"Reminder: The correct answers are shown in bold print above. You need to select all of them.":"")
				+ (getQuestionType()==4?"Reminder: The correct answer will always form a complete, grammatically correct sentence.":"")
				+ (getQuestionType()==5?"Reminder: Your answer must be in the proper numeric format and within " + requiredPrecision + "% of the exact answer, regardless of significant figures.":"")
				+ "</div>Comment:<INPUT TYPE=TEXT SIZE=80 NAME=Notes><INPUT TYPE=SUBMIT NAME=SubmitButton VALUE=Send>"
				+ "</div>"
				+ "</FORM>"
				+ "</div>");

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
		try {
			String[] choiceNames = {"ChoiceAText","ChoiceBText","ChoiceCText","ChoiceDText","ChoiceEText"};
			//buf.append("<input type=hidden name=QuestionType value=" + this.getQuestionType() + ">"
			//		+ "<input type=hidden name=PointValue value=" + this.pointValue + ">");
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
				buf.append("<FONT SIZE=-2 COLOR=FF0000>Enter the correct numerical value to " 
						+ "<input size=5 name=RequiredPrecision value='" + requiredPrecision + "'> "
						+ "% precision. Express scientific notation like 4.294E-15</FONT><br>");
				buf.append("Correct answer:");
				buf.append("<INPUT TYPE=TEXT NAME=CorrectAnswer VALUE='" + correctAnswer + "'> ");
				buf.append(" Units:<INPUT TYPE=TEXT NAME=QuestionTag SIZE=8 VALUE='" 
						+ CharHider.quot2html(CharHider.amp2html(tag)) + "'><br>");
				buf.append("Parameters:<input name=ParameterString value='" 
						+ parameterString + "'><br>");
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

	boolean isCorrect(String answer){
		if (answer == null || answer.length() == 0) return false;
		switch (getQuestionType()) {
		case 4:  // Fill-in-the-word
			Collator compare = Collator.getInstance();
			compare.setStrength(Collator.PRIMARY);
			answer = answer.replaceAll("\\W", "");
			String[] correctAnswers = correctAnswer.split(","); // break comma-separated list into array
			for (int i=0;i<correctAnswers.length;i++) {
				if (compare.equals(answer,correctAnswers[i].replaceAll("\\W",""))) return true;
			}
			return false;
		case 5: // Numeric Answer
			answer = answer.replaceAll(",","");  // removes comma separators from numbers
			try {
				double dAnswer = Double.parseDouble(parseString(answer));
				double dCorrectAnswer = Double.parseDouble(parseString(correctAnswer));
				if (dCorrectAnswer == 0.0) return (dAnswer==0.0?true:false); // traps divide-by-zero
				else return (Math.abs((dAnswer-dCorrectAnswer)/dCorrectAnswer)*100 <= requiredPrecision?true:false);
			}
			catch (Exception e) {
				return false;
			}
		default:
			return correctAnswer.equals(answer);
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
		q.correctAnswer = this.correctAnswer;
		q.tag = this.tag;
		q.pointValue = this.pointValue;
		q.parameterString = this.parameterString;
		q.requiresParser = this.requiresParser;
		q.hint = this.hint;
		q.solution = this.solution;
		q.authorId = this.authorId;
		q.contributorId = this.contributorId;
		q.editorId = this.editorId;
		q.notes = this.notes;
		return q;
	}
}
