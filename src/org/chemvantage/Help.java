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
		// TODO Auto-generated method stub
		doGet(request, response);
	}
	
	protected JsonObject validateJWT(String jwt) throws Exception {
		// This method verifies that the token is properly signed and has not expired
		DecodedJWT token = JWT.decode(jwt);            // throws JWTDecodeException if not a valid JWT
		JWT.require(algorithm).build().verify(token);  // throws JWTVerificationException if not valid		
		JsonObject payload = new JsonParser().parse(token.getPayload()).getAsJsonObject();
		return payload;
	}

	protected String showStudentSubmission(JsonObject payload) throws Exception {
		StringBuffer buf = new StringBuffer(Home.header("ChemVantage Help Page") + Home.banner);
		buf.append("<h3>A Student is seeking your help with a ChemVantage assignment</h3>");
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
				buf.append("Assignment: Homework - " + topic.title + "<br>");
				
				Question q = ofy().load().type(Question.class).id(hwt.questionId).safe();
				String hashMe = hwt.userId + hwt.assignmentId;
				q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
				buf.append(q.print(hwt.showWork,""));
				
				break;
			default: throw new Exception("The assignment type was not valid.");
		}
		return "";
	}
}
