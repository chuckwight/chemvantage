/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2022 ChemVantage LLC
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
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

@WebServlet(urlPatterns = {"/itembank","/items"})
public class ItemBank extends HttpServlet {
	@Serial
	private static final long serialVersionUID = 1L;
	private Text text = null;
	
/*
 * This servlet provides access to the ChemVantage question items through an iframe in the index.html page
 */
	
    public ItemBank() {
        super();
    }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		String userRequest = request.getParameter("r");
		if (userRequest==null) userRequest = "";
		
		switch (userRequest) {
		case "topics":
			out.println(getTopics());
			break;
		case "items":
			out.println(sampleItems(request));
			break;
		default:
			response.sendRedirect("/index.html#itembank");
		}
   }
	
	String getTopics() {
		if (this.text == null) this.text = ofy().load().type(Text.class).filter("title","View All Topics").first().now();
		
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("Select a topic");
			for (Chapter c : text.chapters) {
				buf.append("|" + c.title);
			}
		} catch (Exception e) {
			buf.append(e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
	}
	
	String sampleItems(HttpServletRequest request) {
		if (this.text == null) this.text = ofy().load().type(Text.class).filter("title","View All Topics").first().now();
		
		StringBuffer buf = new StringBuffer();
		try {
			Chapter chapter = null;
			try {
				int chapterNumber = Integer.parseInt(request.getParameter("Topic"));
				for (Chapter c : text.chapters) {
					if (c.chapterNumber == chapterNumber) {
						chapter = c;
						break;
					}
				}
			} catch (Exception e) {
			}
			
			String assignmentType = request.getParameter("Type");
			if (assignmentType==null) assignmentType = "";
			switch (assignmentType) {
			case "Quiz":
			case "Homework":
				break;
			default: assignmentType = "";
			}
			boolean showQuestions = (chapter != null && !assignmentType.isEmpty());
			
			if (!showQuestions) return "<br/><br/>Please select a topic and assignment type.";
			
			List<Key<Question>> keys = new ArrayList<Key<Question>>();
			if (chapter != null) {
				for (Long cId : chapter.conceptIds)	keys.addAll(ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("conceptId",cId).keys().list());
			}
			if (keys.size()==0) buf.append("Sorry, this topic contains no questions of this type.");
			
			Random rand = new Random();
			List<Key<Question>> itemKeys = new ArrayList<Key<Question>>();
			while (itemKeys.size() < 3 && keys.size() > 0) {
				itemKeys.add(keys.remove(rand.nextInt(keys.size())));
			}
			
			List<Question> questions = new ArrayList<Question>(ofy().load().keys(itemKeys).values());
			for (Question q : questions) {
				q.setParameters();
				buf.append("<br/>" + q.print() + "<hr>");
			}
		} catch (Exception e) {
			buf.append(e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
	}
}
