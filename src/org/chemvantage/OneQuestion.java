package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/item")
public class OneQuestion extends HttpServlet {
	private static final long serialVersionUID = 137L;
       
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		StringBuffer buf = new StringBuffer();
		String questionId = request.getParameter("q");
		
		if (questionId==null) 
			buf.append("<br/><br/><br/><form method=get>QuestionId: <input type=text size=10 name=q value='" + (questionId==null?"":questionId) + "' />"
				+ "&nbsp;<input type=submit value='Submit' /></form>");
		else {  // retrieve the question and display the HTML code below
			Question q = null;
			Long p = null;
			try {
				try {
					p = Long.parseLong(request.getParameter("p"));
				}catch (Exception e) {
					p = new Date().getTime();
				}
				q = ofy().load().type(Question.class).id(Long.parseLong(questionId)).safe();
				q.setParameters(p);
				
				buf.append("<br/><br/><form method=post action=/item><input type=hidden name=p value=" + p + " />"
						+ q.print() + "<input type=submit value='Submit' />" + "</form>");
			} catch (Exception e){
				buf.append("<br/><br/>Not found.");
			}
		}
		buf.append("<br/><br/>");  // Put some space at the bottom
		response.setContentType("text/html");
		response.getWriter().println(Subject.header() + Subject.banner + buf.toString() + Subject.footer);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		StringBuffer buf = new StringBuffer(Subject.header() + Subject.banner + ajaxJavaScript());
		try {
			long qid=0L;
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					qid = Long.parseLong((String) e.nextElement());
					break;
				} catch (Exception e2) {}
			}
			Question q = ofy().load().type(Question.class).id(qid).safe();
			long p = Long.parseLong(request.getParameter("p"));
			q.setParameters(p);
			String answer = request.getParameter(String.valueOf(qid));
			if (answer==null) answer = "";
			
			if (q.isCorrect(answer)) {
				buf.append("<h2>Congratulations! Your answer is correct.</h2>" 
						+ q.printAllToStudents(answer,true) + "<br/>");
				buf.append("<a href=/item?q=" + q.id + ">Try another version of this question</a><br/><br/>");
			} else if (answer.isEmpty()) { 
				buf.append("<h3>The answer to the question was left blank. Please try again.</h3>");
				buf.append("<form method=post action=/item><input type=hidden name=p value=" + p + " />"
						+ q.print() + "<input type=submit />" + "</form><br/><br/>");
				buf.append("<a href=/>Learn more about ChemVantage here</a><br/><br/>");
			} else {
				switch (q.getQuestionType()) {
				case 5:  // Numeric question
					try {
						Double.parseDouble(q.parseString(answer));  // throws exception for non-numeric answer
						if (!q.agreesToRequiredPrecision(answer)) buf.append("<h3>Your answer was not correct. Please try again.</h3>");
						else if (!q.hasCorrectSigFigs(answer)) buf.append("<h3>Oh, so close! Please try again.</h3>It appears that you've done the calculation correctly, but your answer does not have the correct number of significant figures appropriate for the data given in the question. "
								+ "If your answer ends with a zero, be sure to include a decimal point to indicate which digits are significant.<br/><br/>");
					}
					catch (Exception e2) {
						buf.append("<h3>Your answer has the wrong format. Please try again.</h3>This question requires a numeric response expressed as an integer, decimal number, "
								+ "or number in scientific notation. Your answer was scored incorrect because the program was unable to recognize "
								+ "your answer as one of these types.<br/><br/>");
					}
					break;
				default:  // All other types of questions
					buf.append("<h3>Your answer was not correct. Please try again.</h3>");
				}
				buf.append("<form method=post action=/item><input type=hidden name=p value=" + p + " />"
						+ q.print() + "<input type=submit value='Submit' />" + "</form><br/><br/>"
						+ "<b>The answer submitted was " + answer + "</b>&nbsp;&nbsp;<IMG SRC=/images/xmark.png ALT='X mark' align=middle><br/><br/>");
				buf.append("<a href=/>Learn more about ChemVantage here</a><br/><br/>");
			}
		} catch (Exception e) {
			buf.append("<br/>Failed. " + e.getMessage());
		}
		response.setContentType("text/html");;
		response.getWriter().println(buf.toString() + Subject.footer);
	}
	
	String getSig() {
		long encrypt = new Date(new Date().getTime() + 300000L).getTime();  // 5 minutes
		try {
			long mask = 0xfffL;
			long iv = encrypt & mask;
			long code;
			for (int i=0;i<3;i++) {  // step 1
				code = (mask & encrypt) << 12;
				encrypt = encrypt ^ code;
				mask = mask << 12;
			}
			mask = 0xfffffffff000L;
			encrypt = encrypt ^ (new Random(iv).nextLong() & mask); // step 2
			mask = 0xfffffffffL;
			code = (encrypt & mask) << 12;
			encrypt = encrypt ^ code;  // step 3
		} catch (Exception e) {
			return null;
		}
		return Long.toHexString(encrypt);
	}
	
	String ajaxJavaScript() {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "var sig = '" + getSig() + "';"
		+ "function ajaxSubmit(url,id,note,email) {\n"
		+ "  var xmlhttp;\n"
		+ "  if (url.length==0) return false;\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      document.getElementById('feedback' + id).innerHTML="
		+ "      '<FONT COLOR=RED><b>Thank you. An editor will review your comment. "
		+ "</b></FONT><p>';\n"
		+ "    }\n"
		+ "  }\n"
		+ "  url += '&QuestionId=' + id + '&sig=' + sig + '&Notes=' + note + '&Email=' + email;\n"
		+ "  xmlhttp.open('GET',url,true);\n"
		+ "  xmlhttp.send(null);\n"
		+ "  return false;\n"
		+ "}\n"
		+ "function ajaxStars(nStars) {\n"
		+ "  var xmlhttp;\n"
		+ "  if (nStars==0) return false;\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    var msg;\n"
		+ "    switch (nStars) {\n"
		+ "      case '1': msg='1 star - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=/Feedback?sig=' + sig + '>tell us why</a>.';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=/Feedback?sig=' + sig + '>tell us why</a>.';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=/Feedback?sig=' + sig + '>Click here</a> '"
		+ "                + 'to provide additional feedback.';"
		+ "                break;\n"
		+ "      case '4': msg='4 stars - Thank you';"
		+ "                break;\n"
		+ "      case '5': msg='5 stars - Thank you!';"
		+ "                break;\n"
		+ "      default: msg='You clicked ' + nStars + ' stars.';\n"
		+ "    }\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      document.getElementById('vote').innerHTML=msg;\n"
		+ "    }\n"
		+ "  }\n"
		+ "  xmlhttp.open('GET','Feedback?UserRequest=AjaxRating&NStars='+nStars,true);\n"
		+ "  xmlhttp.send(null);\n"
		+ "  return false;\n"
		+ "}\n"
		+ "function GetXmlHttpObject() {\n"
		+ "  if (window.XMLHttpRequest) { // code for IE7+, Firefox, Chrome, Opera, Safari\n"
		+ "    return new XMLHttpRequest();\n"
		+ "  }\n"
		+ "  if (window.ActiveXObject) { // code for IE6, IE5\n"
		+ "    return new ActiveXObject('Microsoft.XMLHTTP');\n"
		+ "  }\n"
		+ "  return null;\n"
		+ "}\n"
		+ "</SCRIPT>";
	}
}
