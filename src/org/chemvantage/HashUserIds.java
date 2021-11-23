package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

@WebServlet("/HashUserIds")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class HashUserIds extends HttpServlet {
	private static final long serialVersionUID = 137L;
       
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	// this servlet converts plain text userId values to SHA-256 hashed values; called as a Task by LTILaunch servlets
		User u = null;
		Assignment a = null;
		try {
			u = User.getUser(request.getParameter("sig"));		
			a = ofy().load().type(Assignment.class).id(u.getAssignmentId()).safe();
		} catch (Exception e) {
			return;
		}

		String hashedId = Subject.hashId(u.getId());
		switch (a.assignmentType) {
		case "Quiz":
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("userId",u.getId()).limit(500).list();
			for (QuizTransaction t : qts) t.userId = hashedId;
			ofy().save().entities(qts);
			break;
		case "Homework":
			List<HWTransaction> hts = ofy().load().type(HWTransaction.class).filter("userId",u.getId()).limit(500).list();
			for (HWTransaction t : hts) t.userId = hashedId;
			ofy().save().entities(hts);
			break;
		case "PracticeExam":	
			List<PracticeExamTransaction> pets = ofy().load().type(PracticeExamTransaction.class).filter("userId",u.getId()).limit(500).list();
			for (PracticeExamTransaction t : pets) t.userId = hashedId;
			ofy().save().entities(pets);
			break;
		case "VideoTransaction":
			List<VideoTransaction> vts = ofy().load().type(VideoTransaction.class).filter("userId",u.getId()).limit(500).list();
			for (VideoTransaction t : vts) t.userId = hashedId;
			ofy().save().entities(vts);
			break;
		case "Poll":
			List<PollTransaction> pts = ofy().load().type(PollTransaction.class).filter("userId",u.getId()).limit(500).list();
			for (PollTransaction t : pts) t.userId = hashedId;
			ofy().save().entities(pts);
			break;
		}
	
		try {
			Score s = Score.getInstance(u.getId(), a);
			ofy().delete().entity(s);
			s.owner = Key.create(User.class,hashedId);
			ofy().save().entity(s);
		} catch (Exception e) {}

		
		List<Response> responses = new ArrayList<Response>();
		do {
			responses = ofy().load().type(Response.class).filter("userId",u.getId()).limit(500).list();
			for (Response r : responses) r.userId = hashedId;
			if (responses.size() > 0) ofy().save().entities(responses);
		} while (responses.size() == 500);
	}
}

