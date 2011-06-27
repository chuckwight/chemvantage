/*  PracticeZone - A Java web application for online learning
    Copyright (C) 2009 PracticeZone.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.chemvantage;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xeustechnologies.googleapi.spelling.SpellChecker;
import org.xeustechnologies.googleapi.spelling.SpellCorrection;
import org.xeustechnologies.googleapi.spelling.SpellResponse;

public class SpellingChecker extends HttpServlet {

	private static final long serialVersionUID = 137L;

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		request.getSession().invalidate();
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		if ("SpellCheck".equals(request.getParameter("UserRequest"))) {
			out.println(correctedSpelling(request.getParameter("Answer")));
			return;
		}
		out.println(Login.header + testForm() + Login.footer);
	}
	
	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		request.getSession().invalidate();
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Login.header + testResult(request.getParameter("Word")) + Login.footer);
		
	}

	String testForm() {
		StringBuffer buf = new StringBuffer();
		buf.append("<h3>Spelling Checker</h3>");
		buf.append("<FORM NAME=Proposed ACTION=SpellingChecker METHOD=POST>"
				+ "<INPUT id=1 NAME=Word>"
				+ "<div id=status style='font-size:10px'><a href=# onClick=\"javascript: document.getElementById('status').innerHTML='checking...';ajaxSpellCheck(1);\">Check Spelling</a></div>"
				+ "</FORM>");
		buf.append("<SCRIPT TYPE='text/javascript'>"
				+ "function ajaxSpellCheck(id) {\n"
				+ "  var xmlhttp;\n"
				+ "  var answer = document.getElementById(id).value.trim();\n"
				+ "  if (answer.length==0) {\n"
				+ "    document.getElementById('status').innerHTML='Nothing to check';\n"
				+ "    return false;\n"
				+ "  }\n"
				+ "  xmlhttp=GetXmlHttpObject();\n"
				+ "  if (xmlhttp==null) {\n"
				+ "    alert ('Sorry, your browser does not support AJAX!');\n"
				+ "    return false;\n"
				+ "  }\n"
				+ "  xmlhttp.onreadystatechange=function() {\n"
				+ "  var correctedAnswer;\n"
				+ "  var status=document.getElementById('status');\n"
				+ "  var answerField=document.getElementById(id);\n"
				+ "    if (xmlhttp.readyState==4) {\n"
				+ "      correctedAnswer = xmlhttp.responseText.trim();\n"
				+ "      if (correctedAnswer=='Spell checker is offline, sorry') {\n"
				+ "      status.innerHTML=correctedAnswer; return false;\n"
				+ "    }\n"
				+ "    answerField.value=correctedAnswer;\n"
				+ "    if (correctedAnswer==answer) status.innerHTML='Spelling is OK';\n"
				+ "    else status.innerHTML='Did you mean this instead?';\n"
				+ "    }\n"
				+ "  }\n"
				+ "  xmlhttp.open('GET','SpellingChecker?UserRequest=SpellCheck&Answer='+answer,true);\n"
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
				+ "</SCRIPT>");
		return buf.toString();
	}
 
	String testResult(String word) {
		StringBuffer buf = new StringBuffer("<h3>Result</h3>");
		String correctedWord = correctedSpelling(word);
		if (correctedWord.equals("spell checker is offline, sorry")) buf.append(correctedWord);
		else if (correctedWord.equals(word)) buf.append("Spelling OK");
		else buf.append("Did you mean <i>" + correctedWord + "</i>?<br>"
				+ "(your answer was </i>" + word + "</i>)");
		return buf.toString();
	}
	
	static String correctedSpelling(String answer) {
		StringBuffer buf = new StringBuffer();
		try {
			if (answer==null || answer.isEmpty()) return "";
			answer = answer.trim();
			SpellChecker sc = new SpellChecker();
			SpellResponse sr = sc.check(answer);
			SpellCorrection[] scorr = sr.getCorrections();
			if (scorr==null) return answer;
			int i = 0; // position index in original submission
			int j = 0; // offset of correction
			for(int k = 0;k<scorr.length;k++) {
				j = scorr[k].getOffset();
				buf.append(j>i?answer.substring(i,j):"");
				buf.append(scorr[k].getWords()[0]);
				i = answer.indexOf(" ",j+1);
			}
			buf.append(i>0?answer.substring(i):"");
		} catch (Exception e) {
			return "Spell checker is offline, sorry";
		}
		return buf.toString().trim();
	}
	

}