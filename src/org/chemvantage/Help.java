/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2020 ChemVantage LLC
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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet(urlPatterns = {"/Help","/help"})
public class Help extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
   
    /**
     * This servlet allows students to seek help on a Homework assignment by obtaining a Java Web Token (JWT)
     * that can be emailed to an instructor as part of a URL pointing to this servlet. Possession of a valid
     * JWT allows that person (any person) to view the question item (parameterized for the particular student),
     * as well as all of the student's answer submission and the current value of the showWork field.
     * 
     * If the instructor is currently logged into the LMS and connected to ChemVantage, they can enter the URL
     * into a field. The servlet checks the validity of the instructor role and domain, and then allows the
     * instructor to see the student's proposed solution side-by-side with the ChemVantage solution.
     */
	
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// This is the entry point for a URL from any unverified source to view the student submissions
    	PrintWriter out = response.getWriter();
    	response.setContentType("text/html");
    	response.setCharacterEncoding("UTF-8");
    	try {
    		String jwt = request.getParameter("JWT");
    		JsonObject payload = validateJWT(jwt); // throws exception if expired or otherwise invalid
    		out.println(showStudentSubmission(payload));
    	} catch (Exception e) {
    		response.sendError(401, e.getMessage());
    	}
		
    }

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// This is the entry point for authenticated students to request a tokenized URL to get help with a problem.
		// Required elements are:
		// 1) valid id_token (Token)
		// 2) assignmentType (AssignmentType)
		// 3) transactionId  (TransactionId)
		try {
			User user = User.getUser(request.getParameter("Token"));
			if (user == null) throw new Exception("Invalid id_token");
			
			String jwt = createJWT(request);
			
			PrintWriter out = response.getWriter();
			response.setContentType("text/html");
			String iss = "https://" + request.getServerName();
			
			out.println(Home.header("ChemVantage Help Page") + Home.banner + displayHelpUrl(jwt,iss) + Home.footer);
			
		} catch (Exception e) {
			response.sendError(401, e.getMessage());	
		}
	}
	
	protected JsonObject validateJWT(String jwt) throws Exception {
		// This method verifies that the token is properly signed and has not expired
		DecodedJWT token = JWT.decode(jwt);            // throws JWTDecodeException if not a valid JWT
		JWT.require(algorithm).build().verify(token);  // throws JWTVerificationException if not valid		
		JsonObject payload = new JsonParser().parse(new String(Base64.getUrlDecoder().decode(token.getPayload()))).getAsJsonObject();
		return payload;
	}

	protected String showStudentSubmission(JsonObject payload) throws Exception {
		StringBuffer buf = new StringBuffer(Home.header("ChemVantage Help Page") + Home.banner);
		Date exp = new Date(payload.get("exp").getAsLong()*1000L);
		
		buf.append("<h3>A student is seeking your help with a ChemVantage assignment.</h3>");
		buf.append("The information below may help to illuminate the problem. This information may contain updates since "
				+ "the student made this request (i.e., they may have solved the problem on their own).<p>"
				+ "The token contained in the URL you just entered is only valid for 3 days, and will expire at "
				+ exp + ". Please communicate your response directly to the student, not through ChemVantage.<p>");
		/*
		 * The expected contents of the payload are:
		 *  1) aty (AssignmentType: Q, H, or P)
		 *  2) tid (Transaction ID: long)
		 *  3) exp (Expires: 3 days after issuance)
		 */
		switch (payload.get("aty").getAsString()) {
			case "Q": throw new Exception("Not implemented yet, sorry"); 
			case "P": throw new Exception("Not implemented yet, sorry"); 
			case "H":
				HWTransaction hwt = ofy().load().type(HWTransaction.class).id(payload.get("tid").getAsLong()).safe();
				List<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",hwt.userId).filter("questionId",hwt.questionId).order("graded").list();
				hwt = hwTransactions.get(hwTransactions.size()-1); // loads the most recent transaction, regardless of the one in the token
				
				Topic topic = ofy().load().type(Topic.class).id(hwt.topicId).safe();
				buf.append("<h4>Assignment: Homework - " + topic.title + "</h4>");
				
				Question q = ofy().load().type(Question.class).id(hwt.questionId).safe();
				String hashMe = hwt.userId + hwt.assignmentId;
				q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
				buf.append(q.print(hwt.showWork,""));
				
				buf.append("<script>document.getElementById('showWork" + q.id + "').style.display='';</script>");
				
				List<Response> responses = ofy().load().type(Response.class).filter("userId",hwt.userId).filter("questionId",q.id).list();
				Date solved = null;
				StringBuffer tablebuf = new StringBuffer();
				if (responses.size() > 0) {
					tablebuf.append("<table><tr><td>Date/Time (UTC)</td><td>Student Response</td><td>Score</td></tr>");
					for (Response r : responses) {
						if (r.score>0 && (solved==null || solved.after(r.submitted))) solved = r.submitted;
						tablebuf.append("<tr><td>" + r.submitted.toString() + "</td><td align=center>" + r.studentResponse 
							+ "</td><td align=center>" + r.score + "</td></tr>");	
					}
					tablebuf.append("</table><br>");
				}
				if (solved != null) buf.append("<font color=red><b>Note: This question was answered correctly on " + solved + "</b></font><p>");
				buf.append(tablebuf);
				break;
			default: throw new Exception("The assignment type was not valid.");
		}
		buf.append(Home.footer);
		
		return buf.toString();
	}
	
	protected String createJWT(HttpServletRequest request) throws Exception {
		String aty = request.getParameter("AssignmentType");
		if (aty==null) throw new Exception("Missing assignment type");
		switch (aty) {
			case "Quiz": aty = "Q"; break;
			case "Homework": aty = "H"; break;
			case "PracticeExam": aty = "P"; break;
			default: aty = null;
		}
		
		long tid = Long.parseLong(request.getParameter("TransactionId"));
		
		Date now = new Date();
		Date in3Days = new Date(now.getTime() + 2592000000L);
		
		String token = JWT.create()
				.withClaim("aty", aty)
				.withClaim("tid", tid)
				.withExpiresAt(in3Days)
				.sign(algorithm);
		
		return token;
	}
	
	protected String displayHelpUrl(String jwt, String server) {
		StringBuffer buf = new StringBuffer();
		Date exp = new Date(JWT.decode(jwt).getExpiresAt().getTime());
		
		buf.append("<h3>Need Some Help?</h3>");
		buf.append("Here's what to do: Copy the message below, including the entire URL, and send it with your question via email "
				+ " to your instructor. This will enable your instructor to view your responses to the question, including "
				+ "anything that you may have written in the box the was labeled: 'Show your work here'. You can use the link "
				+ "yourself to verify exactly what information your instructor will see.<p>"
				+ "Your email should try to explain why you're stuck, if possible. When solving homework problems, it is important "
				+ "to show your work in the box provided, because that often can illuminate the issue for your instructor.<p>");
		
		buf.append("<hr><p>");
		
		buf.append("To the instructor:<br>"
				+ "The student sending this message is having difficulties solving a problem in ChemVantage. Please click the URL "
				+ "below to view the question item and the student's responses. The link is dynamic will always give current "
				+ "information, so if the student solved the issue on their own or another way, you will see that in the page.<p>"
				+ "The token provided in the link is only valid for 3 days and expires at " + exp + ".<p>");
		
		String url = server + "/help?JWT=" + jwt;
		
		buf.append("<a href=" + url + ">" + url + "</a>");
		
		buf.append("<p>Thank you.");
		return buf.toString();
	}
}
