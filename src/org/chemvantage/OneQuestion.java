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
		StringBuffer buf = new StringBuffer("<h3>Get HTML Code for One Question</h3>");
		buf.append("<form method=get>");
		String questionId = request.getParameter("q");
		buf.append("QuestionId: <input type=text size=10 name=q value='" + (questionId==null?"":questionId) + "' />"
				+ "<input type=submit></form>");
		if (questionId != null) {  // retrieve the question and display the HTML code below
			Question q = null;
			try {
				long p = new Date().getTime();
				q = ofy().load().type(Question.class).id(Long.parseLong(questionId)).safe();
				q.setParameters(p);
				buf.append("<h4>Raw HTML</h4><xmp>" + q.print() + "</xmp>");
				buf.append("<h4>Renders As:</h4>"
						+ "<form method=post>"
						+ "<input type=hidden name=p value=" + p + " />"
						+ q.print()
						+ "<input type=submit />"
						+ "</form>");
			} catch (Exception e){
				buf.append("Not found. " + e.getMessage());
			}
		}
		buf.append("<br/><br/>");  // Put some space at the bottom
		response.setContentType("text/html");
		response.getWriter().println(Subject.header() + Subject.banner + buf.toString() + Subject.footer);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		StringBuffer buf = new StringBuffer(Subject.header() + Subject.banner);
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
			
		} catch (Exception e) {
			buf.append("Failed. " + e.getMessage());
		}
		response.setContentType("text/html");;
		response.getWriter().println(buf.toString() + Subject.footer);
	}

	String getSig() {
		long encrypt = new Date().getTime() + 5400000L;
		try {
			long mask = 0xfffL;
			long iv = encrypt & mask;
			long code;
			for (int i=0;i<3;i++) { 
				code = (mask & encrypt) << 12;
				encrypt = encrypt ^ code;
				mask = mask << 12;
			}
			mask = 0xfffffffff000L;
			encrypt = encrypt ^ (new Random(iv).nextLong() & mask); 
			mask = 0xfffffffffL;
			code = (encrypt & mask) << 12;
			encrypt = encrypt ^ code;  
		} catch (Exception e) {
			encrypt = 0L;
		}
		return Long.toHexString(encrypt);
	}
}
