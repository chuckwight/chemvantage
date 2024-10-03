package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.Key;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/Sage")
public class Sage extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static List<Concept> conceptList = null;
	private static Map<Long,Concept> conceptMap = null;

	public void doGet(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		
		// available only to the dev server
		if (Subject.getProjectId().equals("chem-vantage-hrd")) return;
				
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		//StringBuffer debug = new StringBuffer("Debug: ");
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("User token expired.");
			
			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			
			if (conceptMap == null) refreshConcepts();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			// Get the SageTreansaction for this assignment
			SageTransaction st = ofy().load().type(SageTransaction.class).filter("userId",user.hashedId).filter("assignmentId",a.id).first().now();
			if (st == null) {
				st = new SageTransaction(user.hashedId,a.id,a.conceptIds);
				ofy().save().entity(st).now();
				out.println(welcomePage(user));
				return;
			} else if (!st.conceptIds.equals(a.conceptIds)) {  // the assignment was revised
				st = revisedTransaction(a,st); // symchronize st to the revised assignment
			}

			Concept concept = null;
			try {  // request from menuPage
				Long conceptId = Long.parseLong(request.getParameter("ConceptId"));
				concept = conceptMap.get(conceptId);
			} catch (Exception e) {}

			switch (userRequest) {
			case "AssignSageConcepts":
				out.println(assignConcepts(user,a));
				break;
			case "ShowSummary":
				break;
			case "ConceptDescription":
				out.println(printConceptDescription(user,concept));
				break;
			case "menu":
				out.println(menuPage(user,st));
				break;
			case "InstructorPage":
				out.println(instructorPage(user,a));
			default:
				boolean getHelp = Boolean.parseBoolean(request.getParameter("Help"));
				out.println(poseQuestion(user,st,concept,getHelp));
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}
	
	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		
		// available only to the dev server
		if (Subject.getProjectId().equals("chem-vantage-hrd")) return;
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		StringBuffer debug = new StringBuffer("Debug: ");

		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("User token expired.");

			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			
			if (conceptMap == null) refreshConcepts();
			
			SageTransaction st = ofy().load().type(SageTransaction.class).filter("userId",user.hashedId).filter("assignmentId",a.id).first().safe();
			
			Concept concept = null;
			try {  // request from menuPage
				Long conceptId = Long.parseLong(request.getParameter("ConceptId"));
				concept = conceptMap.get(conceptId);
			} catch (Exception e) {}
			/*	
				if (st.scores[st.conceptIds.indexOf(conceptId)] == 0) {  // score on this concept is zero
					out.println(printConceptDescription(user,concept));
					return;
				} else {
					boolean getHelp = Boolean.parseBoolean(request.getParameter("Help"));
					out.println(poseQuestion(user,st,concept,getHelp));
				}
			} catch (Exception e) {
				out.println(menuPage(user,st));
			}
			*/
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			debug.append("1");

			switch (userRequest) {
			case "AddConcept":
				if (!a.conceptIds.contains(concept.id)) {
					a.conceptIds.add(concept.id);
					ofy().save().entity(a).now();
				}
				out.println(assignConcepts(user,a));
				break;
			case "Ask Sage":
				try {
					String userPrompt = request.getParameter("UserPrompt");
					String nonce = request.getParameter("Nonce");
					if (!Nonce.isUnique(nonce)) throw new Exception("Only one question is allowed per concept level.");
					int score = st.scores[st.conceptIds.indexOf(concept.id)];
					out.println(askSage(user,concept.id,score,userPrompt));
					return;
				} catch (Exception e) {
					out.println(Subject.header("Sage")
							+ "<h1>Sorry, Sage cannot answer your question.</h1>"
							+ (e.getMessage()==null?e.toString():e.getMessage()) + "<p>"
							+ "Your session will continue in a moment."
							+ "<script>"
							+ " setTimeout(() => { window.location.replace('/Sage?sig=" + user.getTokenSignature() + "&ConceptId=" + concept.id + "'); }, 2000);"  // pause, then continue
							+ "</script>"
							+ Subject.footer);
				}
				break;
			case "DeleteConcept":
				if (a.conceptIds.remove(concept.id)) ofy().save().entity(a).now();
				out.println(assignConcepts(user,a));
				break;
			case "Score This Response":  
				try {			
					out.println(printScore(user,request,concept.id,st));
				} catch (Exception e) {
					out.println(e.getMessage()==null?e.toString():e.getMessage());
				}
				break;
			case "Show Full Solution":
				try {	
					debug.append("3");
					out.println(printSolution(user,request,concept.id,st));
				} catch (Exception e) {
					out.println(e.getMessage()==null?e.toString():e.getMessage());
				}
				break;
			default: throw new Exception("Invalid request");
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	static String askAQuestion(User user, Long conceptId, String nonce) {
		StringBuffer buf = new StringBuffer();
		buf.append("<button id=askButton class=btn onClick=showAskForm(); >Ask Sage a Question</button>");
		buf.append("<div id=askForm style='display:none;' >"
				+ "If you have any question for Sage about <b>" + conceptMap.get(conceptId).title + "</b> you may ask it here:<br/>"
				+ "<form method=post action=/Sage onsubmit='waitForScore();'>"
				+ "<input type=hidden name=Nonce value='" + nonce + "' />"
				+ "<input type=hidden name=ConceptId value='" + conceptId + "' />"
				+ "<input type=hidden name=UserRequest value='Ask Sage' />"
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
				+ "<textarea rows=4 cols=80 name=UserPrompt ></textarea><br/>"
				+ "<input id=ask type=submit class=btn value='Ask Sage' />"
				+ "</form><p>"
				+ "</div>\n");
		
		buf.append("<script>"
				+ "function showAskForm() {"
				+ " document.getElementById('askButton').style='display:none;';"
				+ " document.getElementById('askForm').style='display:inline;';"
				+ "}"
				+ "function waitForScore() {\n"
				+ " let b = document.getElementById('ask');\n"
				+ " b.disabled = true;\n"
				+ " b.value = 'Please wait a moment for Sage to answer.';\n"
				+ "}\n"
				+ "</script>"); 
				
		return buf.toString();
	}

	static String askSage(User user, Long conceptId, int score, String userPrompt) {
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));
		buf.append("<script id='MathJax-script' async src='https://polyfill.io/v3/polyfill.min.js'></script>\n"
				+ " <script id='MathJax-script' async src='https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js'></script>\n");
		
		try {
			BufferedReader reader = null;
			JsonObject api_request = new JsonObject();  // these are used to score essay questions using ChatGPT
			api_request.addProperty("model",Subject.getGPTModel());
			api_request.addProperty("max_tokens",400);
			api_request.addProperty("temperature",0.4);
			
			JsonArray messages = new JsonArray();
			JsonObject m1 = new JsonObject();  // api request message
			m1.addProperty("role", "system");
			m1.addProperty("content","You are a tutor assisting a college student taking General Chemistry. "
					+ "You must restrict your response to the topic " + conceptMap.get(conceptId).title + " in General Chemistry."
					+ "Format the response in HTML and use LaTex math mode specific delimiters as follows:\n"
					+ "inline math mode : `\\(` and `\\)`\n"
					+ "display math mode: `\\[` and `\\]`\n");
			messages.add(m1);;
			JsonObject m2 = new JsonObject();  // api request message
			m2 = new JsonObject();  // api request message
			m2.addProperty("role", "user");
			m2.addProperty("content",userPrompt);
			messages.add(m2);
			api_request.add("messages", messages);
			URL u = new URL("https://api.openai.com/v1/chat/completions");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setRequestMethod("POST");
			uc.setDoInput(true);
			uc.setDoOutput(true);
			uc.setRequestProperty("Authorization", "Bearer " + Subject.getOpenAIKey());
			uc.setRequestProperty("Content-Type", "application/json");
			uc.setRequestProperty("Accept", "application/json");
			OutputStream os = uc.getOutputStream();
			byte[] json_bytes = api_request.toString().getBytes("utf-8");
			os.write(json_bytes, 0, json_bytes.length);           
			os.close();
				
			reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			JsonObject api_response = JsonParser.parseReader(reader).getAsJsonObject();
			reader.close();
			
			String content = api_response.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
			
			buf.append("<h1>Sage Response</h1>");
			
			buf.append("<div style='width:800px;' >");
			buf.append("<img src=/images/sage.png alt='Confucius Parrot' style='margin-left:20px;float:right;' />" + content);	
			buf.append("</div>");
			buf.append("<div id=helpful>"
					+ "<span><b>Was this answer helpful?</b></span> " 
					+ "<a href=#  style='vertical-align:middle' onclick=wasHelpful(true);><img src=/images/thumbs_up.png alt='thumbs up' style='height:30px' /></a>&nbsp;"
					+ "<a href=#  style='vertical-align:middle' onclick=wasHelpful(false);><img src=/images/thumbs_down.png alt='thumbs down' style='height:30px' /></a>"
					+ "</div><p>");
			// include some javascript to process the response
			buf.append("<script>"
					+ "function wasHelpful(response) {"
					+ " document.getElementById('helpful').innerHTML='<br/><b>Thank you for the feedback.</b>';"
					+ " setTimeout(() => { window.location.replace('/Sage?sig=" + user.getTokenSignature() + "&ConceptId=" + conceptId + (score==100?"&UserRequest=menu":"") + "'); }, 1000);"  // pause, then continue
					+ " try {"
					+ "  var xmlhttp = new XMLHttpRequest();"
					+ "  xmlhttp.open('GET','/feedback?UserRequest=HelpfulAnswer&Response=' + response,true);"
					+ "  xmlhttp.send(null);"
					+ " } catch (error) {}"
					+ "}"
					+ "</script>");
		} catch (Exception e) {
			buf.append("<p>Error: " + (e.getMessage()==null?e.toString():e.getMessage()) + "<p>");
		}
		return buf.toString() + Subject.footer;
	}
	
	static String assignConcepts(User user, Assignment a) {
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));
		buf.append("<h1>" + a.title + "</h1>"
				+ "<h2>Select Key Concepts For This Assignment</h2>"
				+ "The key concepts covered in this Sage tutoring assignment are listed below. "
				+ "You may delete any of these or add new concepts to customize this assignment "
				+ "for your class. Students may complete the concepts in any order.<p>");
		buf.append("<ol>");
		
		if (a.conceptIds.isEmpty()) buf.append("<b>This assignment has no key concepts.</b><p>");
		else {
			for (Long conceptId : a.conceptIds) {
				buf.append("<li>"
						+ "<form method=post action=/Sage>" + conceptMap.get(conceptId).title + "&nbsp;"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
						+ "<input type=hidden name=ConceptId value=" + conceptId + " />"
						+ "<input type=hidden name=UserRequest value=DeleteConcept />"
						+ "<input type=submit value=Delete class=btn />"
						+ "</form>"
						+ "</li>");
			}
		buf.append("</ol>");
		}
		
		buf.append("<form method=post action=/Sage>Add a new concept to this assignment: "
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
				+ "<input type=hidden name=UserRequest value=AddConcept />");
		buf.append("<select name=ConceptId><option>Select a concept</option>");
		for (Concept c : conceptList) {
			if (a.conceptIds.contains(c.id)) continue;
			buf.append("<option value=" + c.id + ">" + c.title + "</option>");
		}
		buf.append("</select>&nbsp;"
				+ "<input type=submit value='Add' class=btn />"
				+ "</form><p>");
		
		buf.append("<a href='/Sage?sig=" + user.getTokenSignature() + "&UserRequest=InstructorPage' class=btn>Return to the Instructor Page</a>");
		return buf.toString() + Subject.footer;
	}
	
	static String getHelp(Question q) throws Exception {
		if (q.sageAdvice != null) return q.sageAdvice; // stored AI response
		
		BufferedReader reader = null;
		JsonObject api_request = new JsonObject();  // these are used to score essay questions using ChatGPT
		api_request.addProperty("model",Subject.getGPTModel());
		//api_request.addProperty("max_tokens",200);
		api_request.addProperty("temperature",0.2);
		
		JsonArray messages = new JsonArray();
		JsonObject m1 = new JsonObject();  // api request message
		m1.addProperty("role", "system");
		m1.addProperty("content","You are a tutor assisting a college student taking General Chemistry. "
				+ "The student is requesting your help to answer a homework question. Guide the student "
				+ "in the right general direction, but do not give the correct answer to the question.\n"
				+ "Format your response as HTML. The question is:\n" + q.printForSage());
		messages.add(m1);;
		/*
		JsonObject m2 = new JsonObject();  // api request message
		m2 = new JsonObject();  // api request message
		m2.addProperty("role", "user");
		m2.addProperty("content","Please help me answer this General Chemistry problem: \n"
				+ q.printForSage());
		messages.add(m2);
		*/
		api_request.add("messages", messages);
		URL u = new URL("https://api.openai.com/v1/chat/completions");
		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
		uc.setRequestMethod("POST");
		uc.setDoInput(true);
		uc.setDoOutput(true);
		uc.setRequestProperty("Authorization", "Bearer " + Subject.getOpenAIKey());
		uc.setRequestProperty("Content-Type", "application/json");
		uc.setRequestProperty("Accept", "application/json");
		OutputStream os = uc.getOutputStream();
		byte[] json_bytes = api_request.toString().getBytes("utf-8");
		os.write(json_bytes, 0, json_bytes.length);           
		os.close();
			
		reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
		JsonObject api_response = JsonParser.parseReader(reader).getAsJsonObject();
		reader.close();
		
		String content = api_response.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
		
		// Save the response for the next time a user needs help with this question
		if (!q.requiresParser()) {
			q.sageAdvice = content;
			ofy().save().entity(q);
		}
		
		return content;
	}

	static Long getNewQuestionId(SageTransaction st, Long conceptId) throws Exception {
		// We select the next question by calculating the user's scoreQuintile (1-5) and selecting
		// a question at random from those having a similar degree of difficulty (1-5). The selection 
		// process is random, but a bias is imposed to ensure that there is a 50% chance of selecting 
		// a question where the difficulty is the same as the user's scoreQuintile.
		int[][] qSelCutoff = { {10,17,20,20},{4,14,18,20},{1,5,15,19},{0,2,6,16},{0,0,3,10} };
		
		Long currentQuestionId = st.currentQuestionId;  // don't duplicate this
		int score = st.scores[st.conceptIds.indexOf(conceptId)];
		int scoreQuintile = score==100?4:score/20;			// ranges from 0-4
		int nConceptQuestions = ofy().load().type(Question.class).filter("conceptId",conceptId).count();
		if (nConceptQuestions == 0) throw new Exception("Sorry, there are no questions for this Concept.");
		
		// select a level of difficulty between 0-4 based on user's scoreQuintile
		Random rand = new Random();
		int r = rand.nextInt(20);
		int difficulty = 4;
		for (int i=0;i<4;i++) {
			if (r < qSelCutoff[scoreQuintile][i]) {
				difficulty = i;
				break;
			}
		}
		int nQuintileQuestions =  ofy().load().type(Question.class).filter("conceptId",conceptId).filter("difficulty",difficulty).count();
		
		// select one question index at random
		Key<Question> k = null;
		if (nQuintileQuestions >= 5) {
			k = ofy().load().type(Question.class).filter("conceptId",conceptId).filter("difficulty",difficulty).offset(rand.nextInt(nQuintileQuestions)).keys().first().safe();
		} else {  // use the full range of questions for this Concept
			k = ofy().load().type(Question.class).filter("conceptId",conceptId).offset(rand.nextInt(nConceptQuestions)).keys().first().safe();	
		}
		
		// If this duplicates the current question, try again (recursively)
		if (k.getId().equals(currentQuestionId) && nConceptQuestions > 1) return getNewQuestionId(st,conceptId);
		
		return k.getId();
	}
	
	static String instructorPage(User user, Assignment a) {
	if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();		
		try {
			if (a.title==null) {  // legacy assignment only provided topicId
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				a.title = t.title;
				if (a.conceptIds.isEmpty()) a.conceptIds = t.conceptIds;
				ofy().save().entity(a).now();
			}
			boolean supportsMembership = a.lti_nrps_context_memberships_url != null;
			
			buf.append("<h1>Sage Tutor</1><h2>" + a.title + "</h2>");
			buf.append("<h3>Instructor Page</h3>"
					+ "Sage is an intelligent tutoring app for General Chemistry. You may customize this assignment by selecting the "
					+ "key concepts that will be covered by the tutoring sessions. Students will be introduced to each concept and then "
					+ "presented with a series of questions and problems of progressively increasing difficulty. The AI-powered tutor, "
					+ "Sage, is available to provide guidance when needed, and will provide a detailed step-by-step solutiuon to each "
					+ "problem after the student submits an answer. Each time the student reaches a higher quintile score (20%, 40%, etc) "
					+ "they are offered an opportunity to ask any open-ended question related to the key concept being covered.<p>");
			
			buf.append("From here, you may<UL>"
					+ "<LI><a href='/Sage?UserRequest=AssignSageConcepts&sig=" + user.getTokenSignature() + "'>Customize this assignment</a> by selecting key concepts to be covered.</LI>"
					+ (supportsMembership?"<LI><a href='/Sage?UserRequest=ShowSummary&sig=" + user.getTokenSignature() + "'>Review your students' Sage scores</a></LI>":"")
					+ "</UL><br/>");
			
			buf.append("<a href='/Sage?UserRequest=menu&sig=" + user.getTokenSignature() + "' class='btn'>Show This Assignment</a><br/><br/>");
			
		} catch (Exception e) {
			buf.append("<br/>Instructor page error: " + e.getMessage());
		}
		return buf.toString();
	}

	static String menuPage (User user, SageTransaction st) {
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));

		try {
			buf.append("<h1>Sage Is Your Tutor</h1>"
					+ "Select any of the key concepts listed below for this assignment.<br/>"
					+ "Your current scores are indicated in parentheses.<p>");

			//	construct an ordered list of assigned key concepts
			buf.append("<ol>");
			for (Long cId : st.conceptIds) {
				buf.append("<li><a href=/Sage?ConceptId=" + cId + "&sig=" + user.getTokenSignature() + (st.scores[st.conceptIds.indexOf(cId)]==0?"&UserRequest=ConceptDescription":"") + ">" 
						+ conceptMap.get(cId).title + "</a>" 
						+ "&nbsp;(" + st.scores[st.conceptIds.indexOf(cId)] + "%)</li>\n");
			}
			buf.append("</ol>");

			return buf.toString() + Subject.footer;
		} catch (Exception e) {
			return e.getMessage()==null?e.toString():e.getMessage();
		}
	}

	static String orderResponses(String[] answers) {
		if (answers==null) return "";
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}

	static String poseQuestion(User user, SageTransaction st, Concept concept, boolean getHelp) throws Exception {
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));
		try {
			if (st.currentQuestionId == null) {
				st.currentQuestionId = getNewQuestionId(st,concept.id);
				st.currentParameter = new Random().nextLong(Long. MAX_VALUE);
				ofy().save().entity(st).now();
			}
			Question q = ofy().load().type(Question.class).id(st.currentQuestionId).now();
			q.setParameters(st.currentParameter);
	
			buf.append("<h1>" + concept.title + "</h1>");
			
			buf.append("<div style='width:800px; height=300px; overflow=auto; display:flex; align-items:center;'>");
			if (getHelp) {
				buf.append("<script id='MathJax-script' async src='https://polyfill.io/v3/polyfill.min.js'></script>\n"
						+ " <script id='MathJax-script' async src='https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js'></script>\n");
				
				buf.append("<div>"
						+ getHelp(q)
						+ "</div>"
						+ "<img src=/images/sage.png alt='Confucius Parrot' style='float:right'>"
						+ "</div>");
				
				buf.append("<div id=helpful>"
						+ "<span><b>Is this helpful?</b></span> " 
						+ "<a href=#  style='vertical-align:middle' onclick=wasHelpful(true);><img src=/images/thumbs_up.png alt='thumbs up' style='height:30px' /></a>&nbsp;"
						+ "<a href=#  style='vertical-align:middle' onclick=wasHelpful(false);><img src=/images/thumbs_down.png alt='thumbs down' style='height:30px' /></a>"
						+ "</div><p>");
				// include some javascript to process the response
				buf.append("<script>"
						+ "function wasHelpful(response) {"
						+ " document.getElementById('helpful').innerHTML='<br/>Thank you for the feedback. ' "
						+ "  + (response?'I&apos;m always happy to help.':'I&apos;ll try to do better next time.');"
						+ " try {"
						+ "  var xmlhttp = new XMLHttpRequest();"
						+ "  xmlhttp.open('GET','/feedback?UserRequest=HelpfulHint&QuestionId=" + q.id + "&Response=' + response,true);"
						+ "  xmlhttp.send(null);"
						+ " } catch (error) { console.error(error); }"
						+ "}"
						+ "</script>");
			} else {
				buf.append("<div>"
						+ "Please submit your answer to the question below.<p>"
						+ "If you get stuck, I am here to help you, but your score will be higher if you do it by yourself.<p>"
						+ "<a id=help class=btn role=button href=/Sage?sig=" + user.getTokenSignature() + "&Help=true&ConceptId=" + concept.id + "&p=" + st.currentParameter + " onclick=waitForHelp(); >Please help me with this question</a>"
						+ "</div>"
						+ "<img src=/images/sage.png alt='Confucius Parrot' style='float:right'>"
						+ "</div>");
				// include some javascript to change the submit button
				buf.append("<script>"
						+ "function waitForHelp() {"
						+ " let a = document.getElementById('help');"
						+ " a.innerHTML = 'Please wait a moment for Sage to answer.';"
						+ "}"
						+ "</script>");
				}
			
			buf.append("<hr style='width:800px;margin-left:0'>");  // break between Sage helper panel and question panel
	
			// Print the question for the student
			buf.append("<form method=post style='max-width:800px;' onsubmit='waitForScore();' >"
					+ "<input type=hidden name=QuestionId value='" + q.id + "' />"
					+ "<input type=hidden name=ConceptId value='" + q.conceptId + "' />"
					+ "<input type=hidden name=Parameter value='" + st.currentParameter + "' />"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<input type=hidden name=UserRequest value='Score This Response' />"
					+ q.print()
					+ "<input id='sub" + q.id + "' type=submit class='btn' />"
					+ "</form><p>");
			
			// include some javascript to change the submit button
			buf.append("<script>"
					+ "function waitForScore() {\n"
					+ " let b = document.getElementById('sub" + q.id + "');\n"
					+ " b.disabled = true;\n"
					+ " b.value = 'Please wait a moment while we score your response.';\n"
					+ "}\n"
					+ "</script>");
		} catch (Exception e) {
			buf.append("<p>Error: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString() + Subject.footer;	
	}

	static String printConceptDescription(User user, Concept concept) throws Exception {
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));
		try {
			if (concept == null) throw new Exception("No concept was specified for this requeat.");
			buf.append("<h1>" + concept.title + "</h1>"
					+ "<div style='max-width:800px'>"
					+ "<img src=/images/sage.png alt='Confucius Parrot' style='float:right;margin:20px;'>"
					+ concept.getSummary() + "<p>"
					+ "<a class=btn role=button href='/Sage?sig=" + user.getTokenSignature() + "&ConceptId=" + concept.id + "'>Continue</a>"
					+ "</div>");
	
		} catch (Exception e) {
			buf.append("<p>Error: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString() + Subject.footer;
	}

	static String printScore(User user, HttpServletRequest request, Long conceptId, SageTransaction st) throws Exception {
		// Prepare a section that allows the user to ask Sage a question
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));
		
		int rawScore = 0;  // result is 0, 1 or 2. Tne partial score 1 is for wrong sig figs.
		
		Long questionId = Long.parseLong(request.getParameter("QuestionId"));
		if (!questionId.equals(st.currentQuestionId)) throw new Exception("Wrong question is being scored.");
		
		String[] responses = request.getParameterValues(Long.toString(questionId));
		String studentAnswer = orderResponses(responses);
		if (studentAnswer == null || studentAnswer.isEmpty()) return null;
		
		Question q = ofy().load().type(Question.class).id(questionId).safe();
		long p = 0L;
		if (q.requiresParser()) {
			p = Long.parseLong(request.getParameter("Parameter"));
			q.setParameters(p);
		}
		
		// Construct a link that either reveals the static solution or submits a request to generate a full AI solution for parameterized questions
		StringBuffer showMeLink = new StringBuffer("<div>");
				
		// Get the raw score for the student's answer
		switch (q.getQuestionType()) {
		case 1:
		case 2:
		case 3:
		case 4:
		case 5:
			rawScore = q.isCorrect(studentAnswer)?2:q.agreesToRequiredPrecision(studentAnswer)?1:0;
			if (q.requiresParser()) {  // offer a POST form to get an AI response
				// include some javascript to change the submit button
				showMeLink.append("<script>"
						+ "function waitForScore() {\n"
						+ " let b = document.getElementById('showFullSolution');\n"
						+ " b.disabled = true;\n"
						+ " b.value = 'Please wait a moment for Sage to respond.';\n"
						+ "}\n"
						+ "</script>");
				showMeLink.append("<form method=post action=/Sage onsubmit='waitForScore();'>"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
						+ "<input type=hidden name=ConceptId value=" + conceptId + " />"
						+ "<input type=hidden name=QuestionId value=" + q.id + " />"
						+ "<input type=hidden name=Parameter value=" + p + " />");
				for (String r : responses) showMeLink.append("<input type=hidden name=" + q.id + " value='" + r + "' />");
				showMeLink.append("<input type=hidden name=RawScore value=" + rawScore + " />"
						+ "<input type=hidden name=UserRequest value='Show Full Solution' />"
						+ "<input id=showFullSolution type=submit class=btn value='Show me' />"
						+ "</form>");
			} else {  // offer the static solution
				showMeLink.append("<script>"
						+ "function showSolution() {"
						+ " document.getElementById('link').style.display='none';"
						+ " document.getElementById('solution').style.display='inline';"
						+ "}"
						+ "</script>");
				showMeLink.append("<div id=link><a href=# class=btn role=button onclick='showSolution();'>Show me</a></div>"
						+ "<div id=solution style='display: none'>" 
						+ q.printAllToStudents(studentAnswer) 
						+ "</div>");
			}
			break;
		case 6:  // Handle five-star rating response
			try {
				if (Integer.parseInt(studentAnswer) > 0) rawScore = 2;  // full marks for submitting a response
			} catch (Exception e) {}
			break;
		case 7:  // New section for scoring essay questions with Chat GPT
			JsonObject api_score = scoreEssayQuestion(q.text,studentAnswer);  // these are used to score essay questions using ChatGPT
			rawScore = api_score.get("score").getAsInt()/2; 	// scale 0-2
			showMeLink.append(api_score.get("feedback").getAsString());  // displays feedback in lieu of link
			break;
		default: throw new Exception("Unable to determine question type");
		}

		showMeLink.append("</div>");
		
		// Update and save the Score object
		boolean level_up = st.update(rawScore,conceptId);
		ofy().save().entity(st).now();
		
		try {
			if (q.getQuestionType() == 6) {
				buf.append(rawScore==2?"<h1>Thank you for your rating.</h1><b>You received full credit for this question.</b>":"<h1>No rating was submitted.</h1><b>You did not receive credit for this question.</b>");
			} else {
				switch (rawScore) {  // 0, 1 or 2
				case 2:  // correct answer
					buf.append("<h1>Congratulations!</h1>"
							+ "<b>Your answer is correct. </b><IMG SRC=/images/checkmark.gif ALT='Check mark' align=bottom /><p>"
							+ "<div style='width:800px;display:flex;align-items:center;'>"
							
							+ showMeLink
							+ "<img id=polly src='/images/parrot.png' alt='Parrot character' style='margin-left:20px;'>"
							+ "</div>");
					break;
				case 1: // partial credit			
					buf.append("<h1>Your answer is partially correct</h1>"
							+ "<b>You received half credit.</b><p>"
							+ "<div style='width:800px;display:flex; align-items:center;'>"
							+ showMeLink
							+ "<img id=polly src='/images/parrot1.png' alt='Parrot character' style='margin-left:20px;'>"
							+ "</div>");
					break;
				case 0: // wrong answer
					buf.append("<h1>Sorry, your answer is not correct.<IMG SRC=/images/xmark.png ALT='X mark' align=middle></h1>"
							+ "<div style='width:800px;display:flex; align-items:center;'>"
							+ showMeLink
							+ "<img id=polly src='/images/parrot0.png' alt='Parrot character' style='margin-left:20px;'>\n"
							+ "</div>");
					break;
				}
			}
			
			int score = st.scores[st.conceptIds.indexOf(conceptId)];
			if (score == 100) { 
				buf.append("<h2>Your score is 100%</h2>");
				buf.append("You have mastered the concept: <b>" + conceptMap.get(conceptId).title +"</b>.<p>");
				if (level_up) buf.append(askAQuestion(user, conceptId, Nonce.generateNonce()));
			} else if (level_up) {
				buf.append("<h3>You have moved up to Level " + (score/20 + 1) + ".</h3>"
						+ "<b>Your current score on this concept is " + score + "%.</b><p>");
				buf.append(askAQuestion(user,conceptId,Nonce.generateNonce()) + " Otherwise...");
			} else {
				buf.append("<p><b>Your current score on this concept is " + score + "%.</b>&nbsp;");
			}
			
			// print a button to continue
			buf.append("<a class=btn role=button href='/Sage?sig=" + user.getTokenSignature() + "&ConceptId=" + conceptId + (score==100?"&UserRequest=menu":"") + "'>Continue</a><p>");
		} catch (Exception e) {
			buf.append("<p>" + e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString() + Subject.footer;
	}

	static String printSolution(User user, HttpServletRequest request, Long conceptId, SageTransaction st) throws Exception {
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));
		int rawScore = Integer.parseInt(request.getParameter("RawScore"));
		switch (rawScore) {
		case 2:
			buf.append("<h1>Congratulations!</h1>"
					+ "<b>Your answer is correct. </b><IMG SRC=/images/checkmark.gif ALT='Check mark' align=bottom /><p>");
			break;
		case 1:
			buf.append("<h1>Your answer is partially correct</h1>"
					+ "<b>You received half credit.</b><p>");
			break;
		case 0:
			buf.append("<h1>Sorry, your answer is not correct.<IMG SRC=/images/xmark.png ALT='X mark' align=middle></h1>");
			break;
		default:
		}
		long questionId = Long.parseLong(request.getParameter("QuestionId"));
		long p = Long.parseLong(request.getParameter("Parameter"));
		String[] responses = request.getParameterValues(Long.toString(questionId));
		String studentAnswer = orderResponses(responses);
		Question q = ofy().load().type(Question.class).id(questionId).safe();
		q.setParameters(p);
		buf.append("<div style='width:800px;display:flex;align-items:center;'>"
				+ "<div style='width:600px'>" + q.printAllToStudents(studentAnswer) + "</div><p>");
		switch (rawScore) {
		case 2:
			buf.append("<img id=polly src='/images/parrot.png' alt='Parrot character' style='margin-left:20px;'>");
			break;
		case 1:
			buf.append("<img id=polly src='/images/parrot1.png' alt='Parrot character' style='margin-left:20px;'>");
			break;
		case 0:
			buf.append("<img id=polly src='/images/parrot0.png' alt='Parrot character' style='margin-left:20px;'>");
			break;
		}
		buf.append("</div>");			
		
		// print a button to continue
		int score = st.scores[st.conceptIds.indexOf(conceptId)];
		buf.append("<a class=btn role=button href='/Sage?sig=" + user.getTokenSignature() + "&ConceptId=" + conceptId + (score==100?"&UserRequest=menu":"") + "'>Continue</a><p>");
		return buf.toString() + Subject.footer;
	}

	static void refreshConcepts() {
		conceptList = ofy().load().type(Concept.class).order("orderBy").list();
		conceptMap = new HashMap<Long,Concept>();
		for (Concept c : conceptList) conceptMap.put(c.id, c);
	}

	static SageTransaction revisedTransaction(Assignment a, SageTransaction st) throws Exception {
		// this method creates a revised version of the SageTransaction 
		// in case the assignment conceptIds is revised by the instructor
		SageTransaction revised = new SageTransaction(st.userId, a.id, a.conceptIds);
		for (int i=0; i<a.conceptIds.size(); i++) {  // copy the scores if they exist
			int j = st.conceptIds.indexOf(a.conceptIds.get(i)); // index of conceptId in old st; may be -1
			revised.scores[i] = j==-1?0:st.scores[j];
		}
		revised.id = st.id;
		revised.answeredIds = st.answeredIds;
		revised.created = st.graded;
		revised.graded = st.graded;
		revised.currentQuestionId = st.currentQuestionId;
		revised.currentParameter = st.currentParameter;
		revised.helped = st.helped;
		return revised;
	}
	
	static JsonObject scoreEssayQuestion(String questionText, String studentAnswer) throws Exception {
		if (studentAnswer.length()>800) studentAnswer = studentAnswer.substring(0,799);
		JsonObject api_request = new JsonObject();  // these are used to score essay questions using ChatGPT
		api_request.addProperty("model",Subject.getGPTModel());
		api_request.addProperty("max_tokens",200);
		api_request.addProperty("temperature",0.2);
		JsonObject m = new JsonObject();  // api request message
		m.addProperty("role", "user");
		String prompt = "Question: \"" + questionText +  "\"\n My response: \"" + studentAnswer + "\"\n "
				+ "Using JSON format, give a score for my response (integer in the range 0 to 5) "
				+ "and feedback for how to improve my response.";
		m.addProperty("content", prompt);
		JsonArray messages = new JsonArray();
		messages.add(m);
		api_request.add("messages", messages);
		URL u = new URL("https://api.openai.com/v1/chat/completions");
		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
		uc.setRequestMethod("POST");
		uc.setDoInput(true);
		uc.setDoOutput(true);
		uc.setRequestProperty("Authorization", "Bearer " + Subject.getOpenAIKey());
		uc.setRequestProperty("Content-Type", "application/json");
		uc.setRequestProperty("Accept", "application/json");
		OutputStream os = uc.getOutputStream();
		byte[] json_bytes = api_request.toString().getBytes("utf-8");
		os.write(json_bytes, 0, json_bytes.length);           
		os.close();
		
		BufferedReader reader = null;
		reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
		JsonObject api_response = JsonParser.parseReader(reader).getAsJsonObject();
		reader.close();
		
		// get the ChatGPT score from the response:
		JsonObject api_score = null;
		try {
			String content = api_response.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
			api_score = JsonParser.parseString(content).getAsJsonObject();
			return api_score;
		} catch (Exception e) {
			api_score = new JsonObject();
			api_score.addProperty("score", 0);
			api_score.addProperty("feedback", "Sorry, an error occurred: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return api_score;
	}
	
	static String showSummary(User user,Assignment a) {
		StringBuffer buf = new StringBuffer();
		if (a==null) return "No assignment was specified for this request.";

		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";

		try {
			if (a.lti_nrps_context_memberships_url==null) throw new Exception("No Names and Roles Provisioning support.");

			buf.append("<h1>Sage Tutor Scores</h1>");
			buf.append("Title: " + a.title + "<br/>");
			buf.append("Assignment ID: " + a.id + "<br/>");
			buf.append("Valid: " + new Date() + "<p>");
			buf.append("The roster below is obtained using the Names and Role Provisioning service offered by your learning management system, "
					+ "and may or may not include user's names or emails, depending on the settings of your LMS.<br/><br/>");

			Map<String,String> scores = LTIMessage.readMembershipScores(a);
			if (scores==null) scores = new HashMap<String,String>();  // in case service call fails

			Map<String,String[]> membership = LTIMessage.getMembership(a);
			if (membership==null) membership = new HashMap<String,String[]>(); // in case service call fails

			List<SageTransaction> sTList = ofy().load().type(SageTransaction.class).filter("assignmentId",a.id).list();
			Map<String,SageTransaction> stMap = new HashMap<String,SageTransaction>();
			for (SageTransaction sT : sTList) stMap.put(sT.userId, sT);
			
			Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
			String platform_id = d.getPlatformId() + "/";
			
			buf.append("<table><tr><th>&nbsp;</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>CV Score</th><th>Scores Detail</th></tr>");
			int i=0;
			int nMismatched = 0;
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				if (entry == null) continue;
				String lmsScoreString = scores.get(entry.getKey());
				lmsScoreString = (lmsScoreString==null?" - ":lmsScoreString + "%");
				String hashedId = Subject.hashId(platform_id + entry.getKey());
				SageTransaction st = stMap.get(hashedId);
				int cvScore = 0;
				for (int s : st.scores) cvScore += s;  // total score for all concepts
				cvScore = cvScore / st.conceptIds.size();  // overall percent score
				String cvScoreString = String.valueOf(cvScore) + "%";
				boolean synched = !"Learner".equals(entry.getValue()[0]) || cvScoreString.equals(lmsScoreString);
				String forUserId = platform_id + entry.getKey();  // only send hashed values through links
				i++;
				buf.append("<tr><td>" + i + ".&nbsp;</td>"
						+ "<td>" + entry.getValue()[1] + "</td>"
						+ "<td>" + entry.getValue()[2] + "</td>"
						+ "<td>" + entry.getValue()[0] + "</td>"
						+ "<td align=center>" + lmsScoreString + "</td>"
						+ "<td align=center>" + cvScoreString + "</td>"
						//+ "<td align=center><a href=/Homework?UserRequest=Review&sig=" + user.getTokenSignature() + "&ForUserId=" + forUserId + "&ForUserName=" + entry.getValue()[1].replaceAll(" ","+") + ">show</a></td>"
						+ (synched?"":"<td><span id='cell" + forUserId + "'><button onClick=this.disabled=true;this.style.opacity=0.5;synchronizeScore('" + forUserId + "','" + user.getTokenSignature() + "','/Sage'); >sync</button></span></td>")
						+ "</tr>");
				// Flag this score set as unsynchronized only if there is one or more non-null ChemVantage Learner score that is not equal to the LMS score
				// Ignore Instructor scores because the LMS often does not report them, and ignore null cvScore entities because they cannot be reported.
				if (!synched) nMismatched++;
			}
			buf.append("</table><br/>");
			if (nMismatched > 0) {
				//buf.append(ajaxJavaScript(user.getTokenSignature()));
				buf.append("You may use the individual 'sync' buttons above to resubmit any ChemVantage score to the LMS. Note that in some cases, mismatched scores are expected (e.g., when "
						+ "the instructor overrides a score or when a late submission is not accepted by the LMS). You may have to adjust the settings in your LMS to accept the "
						+ "revised score (e.g., change the due date, grade override or allowed number of submissions). ");
			}
			if (nMismatched>1) {
				buf.append("Use the button below to synchronize all of the Learner scores. This might take a minute, depending on the number of mismatches.<br/>"
					+ "<form method=post action=/Homework onsubmit=waitforSync(); >"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<input type=hidden name=UserRequest value='Synchronize Scores' />"
					+ "<input type=submit id=syncAll value='Synchronize All Scores' />"
					+ "</form>");
			}
				return buf.toString();
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	static String welcomePage(User user) throws Exception {
		StringBuffer buf = new StringBuffer(Subject.header("Sage"));
		buf.append("<h1>Welcome to Sage</h1>"
				+ "<h2>Sage is an intelligent tutor for General Chemistry.</h2>"
				+ "<div style='max-width:800px;'>"
				+ "<img src=/images/sage.png alt='Confucius Parrot' style='float:right'>"
				+ "This assignment covers one or more key concepts that have been assigned by your "
				+ "instructor. For each concept you will be guided through a series of questions and problems, "
				+ "with the Sage at your side, ready to provide help whenever you need it. "
				+ "Each concept has 5 levels. Whenever you complete a level or finish a concept with a score of 100%, "
				+ "you will have an opportunity to ask Sage a question of your choice about that concept.<p>"
				+ "<a href='/Sage?UserRequest=menu&sig=" + user.getTokenSignature() + "' class=btn>Continue</a>"
				+ "</div>");
		return buf.toString() + Subject.footer;
	}

}
