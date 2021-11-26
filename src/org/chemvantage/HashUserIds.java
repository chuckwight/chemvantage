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
		String uId = null;
		try {
			u = User.getUser(request.getParameter("sig"));		
			a = ofy().load().type(Assignment.class).id(u.getAssignmentId()).safe();
			uId = u.getId();
		} catch (Exception e) {
			return;
		}

		int more = 0;
		switch (a.assignmentType) {
		case "Quiz":
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("userId",uId).limit(500).list();
			more = 100 - qts.size();
			if (more > 0) qts.addAll(ofy().load().type(QuizTransaction.class).filter("userId>","CK").filter("userId<","CL").limit(more).list());
			for (QuizTransaction t : qts) t.userId = Subject.hashId(t.userId);
			if (qts.size()>0) ofy().save().entities(qts);
			break;
		case "Homework":
			List<HWTransaction> hts = ofy().load().type(HWTransaction.class).filter("userId",uId).limit(500).list();
			more = 100 - hts.size();
			if (more > 0) hts.addAll(ofy().load().type(HWTransaction.class).filter("userId>","CK").filter("userId<","CL").limit(more).list());
			for (HWTransaction t : hts) t.userId = Subject.hashId(t.userId);
			if (hts.size()>0) ofy().save().entities(hts);
			break;
		case "PracticeExam":	
			List<PracticeExamTransaction> pets = ofy().load().type(PracticeExamTransaction.class).filter("userId",uId).limit(500).list();
			more = 100 - pets.size();
			if (more > 0) pets.addAll(ofy().load().type(PracticeExamTransaction.class).filter("userId>","CK").filter("userId<","CL").limit(more).list());
			for (PracticeExamTransaction t : pets) t.userId = Subject.hashId(t.userId);
			if (pets.size()>0) ofy().save().entities(pets);
			break;
		case "VideoTransaction":
			List<VideoTransaction> vts = ofy().load().type(VideoTransaction.class).filter("userId",uId).limit(500).list();
			more = 100 - vts.size();
			if (more > 0) vts.addAll(ofy().load().type(VideoTransaction.class).filter("userId>","CK").filter("userId<","CL").limit(more).list());
			for (VideoTransaction t : vts) t.userId = Subject.hashId(t.userId);
			if (vts.size()>0) ofy().save().entities(vts);
			break;
		case "Poll":
			List<PollTransaction> pts = ofy().load().type(PollTransaction.class).filter("userId",uId).limit(500).list();
			more = 100 - pts.size();
			if (more > 0) pts.addAll(ofy().load().type(PollTransaction.class).filter("userId>","CK").filter("userId<","CL").limit(more).list());
			for (PollTransaction t : pts) t.userId = Subject.hashId(t.userId);
			if (pts.size()>0) ofy().save().entities(pts);
			break;
		}
	
		String hashedId = Subject.hashId(uId);
		try {
			Score s = Score.getInstance(uId, a);
			if (s!=null) {
				ofy().delete().entity(s);
				s.owner = Key.create(User.class,hashedId);
				ofy().save().entity(s);
			}
		} catch (Exception e) {}

		
		List<Response> responses = new ArrayList<Response>();
		do {
			responses = ofy().load().type(Response.class).filter("userId",uId).limit(500).list();
			for (Response r : responses) r.userId = hashedId;
			if (responses.size() > 0) ofy().save().entities(responses);
		} while (responses.size() == 500);
	}
}

