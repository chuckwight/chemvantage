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
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.Key;

@WebServlet("/item")
public class OneQuestion extends HttpServlet {
	private static final long serialVersionUID = 137L;
       
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		StringBuffer buf = new StringBuffer();
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		Long questionId = null;
		Long parameter = null;
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";
		
		try {
			questionId = Long.parseLong(request.getParameter("q"));
			parameter = Long.parseLong(request.getParameter("p"));
			switch (userRequest) {
			case "GetExplanation":
				Question q = ofy().load().type(Question.class).id(questionId).now();
				if (q.requiresParser()) q.setParameters(parameter);
				out.println(q.getExplanation());
				return;		
			}
		} catch (Exception e) {}

		if (questionId==null) 
			buf.append("<br/><br/><br/><form method=get>QuestionId: <input type=text size=10 name=q />"
					+ "&nbsp;<input type=submit value='Submit' /></form>");
		else {  // retrieve the question and display the HTML code below
			try{
				Question q = ofy().load().type(Question.class).id(questionId).safe();
				if (parameter == null) parameter = new Date().getTime();
				q.setParameters(parameter);

				buf.append("<br/><br/><form method=post action=/item onsubmit=waitForScore(); >"
						+ "<input type=hidden name=p value=" + parameter + " />"
						+ q.print() + "<input id=SubmitButton type=submit value='Submit' />" + "</form>");
				buf.append("<SCRIPT>"
						+ "function waitForScore() {"
						+ " let b = document.getElementById('SubmitButton');"
						+ " b.disabled = true;"
						+ " b.value = 'Please wait a moment while we score your response.';"
						+ "}"
						+ "</SCRIPT>");
			} catch (Exception e){
				buf.append("<br/><br/>Not found.");
			}
		}
		buf.append("<br/><br/>");  // Put some space at the bottom
		response.getWriter().println(Subject.header() + "<h1>Question Item</h1>" + buf.toString() + Subject.footer);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String signature = new User().getTokenSignature();
		StringBuffer buf = new StringBuffer(Subject.header() + "<h1>Question Item</h1>" + ajaxJavaScript(signature));
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
			String answer = orderResponses(request.getParameterValues(String.valueOf(qid)));
			
			if (q.isCorrect(answer)) {
				buf.append("<h2>Congratulations! Your answer is correct.</h2>" 
						+ q.printAllToStudents(answer,true) 
						+ " <p>\n"
						+ " <div id=explanation style='max-width:800px'>"
						+ " <button id=explainThis class=btn onclick=getExplanation();>Please explain this answer</button>"
						+ " </div>\n"
						+ "<script>\n"
						+ "function getExplanation() {\n"
						+ "  document.getElementById('explainThis').innerHTML='Please wait a moment...';\n"
						+ "  try {\n"
						+ "    var xmlhttp = GetXmlHttpObject();\n"
						+ "    if (xmlhttp==null) {\n"
						+ "      alert('Sorry, your browser does not support AJAX!');\n"
						+ "	     return false;\n"
						+ "    }\n"
						+ "	   xmlhttp.onreadystatechange=function() {\n"
						+ "      if (xmlhttp.readyState==4) {\n"
						+ "        document.getElementById('explanation').innerHTML = xmlhttp.responseText;\n"  // Sage explanation
						+ "        var mathjax = document.createElement('script');\n"
						+ "        mathjax.type = 'text/javascript';\n"
						+ "        mathjax.src = 'https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js';\n"
						+ "        document.head.appendChild(mathjax);\n"
						+ "      }\n"
						+ "    }\n"
						+ "  } catch (error) {}\n"
						+ "  xmlhttp.open('GET','/item?UserRequest=GetExplanation&q=" + q.id + "&p=" + p + "',true);\n"
						+ "  xmlhttp.send(null);\n"
						+ "}\n"
						+ "</script>\n"
						+ "</div><p>\n");
				//buf.append("<a href=/item?q=" + q.id + ">Try another version of this question</a><br/><br/>");
			} else if (answer.isEmpty()) { 
				buf.append("<h3>The answer to the question was left blank.</h3>");
				buf.append("<form method=post action=/item><input type=hidden name=p value=" + p + " />"
						+ q.print() + "<br/><input type=submit />" + "</form><br/><br/>");
				buf.append("<a href=/>Learn more about ChemVantage here</a><br/><br/>");
			} else {
				switch (q.getQuestionType()) {
				case 5:  // Numeric question
					try {
						Double.parseDouble(q.parseString(answer));  // throws exception for non-numeric answer
						if (!q.agreesToRequiredPrecision(answer)) buf.append("<h3>Your answer was not correct.</h3>");
						else if (!q.hasCorrectSigFigs(answer)) buf.append("<h3>Oh, so close!</h3>It appears that you've done the calculation correctly, but your answer does not have the correct number of significant figures appropriate for the data given in the question. "
								+ "If your answer ends with a zero, be sure to include a decimal point to indicate which digits are significant.<br/><br/>");
					}
					catch (Exception e2) {
						buf.append("<h3>Your answer has the wrong format.</h3>This question requires a numeric response expressed as an integer, decimal number, "
								+ "or number in scientific E notation (example: 6.022E-23). Your answer was scored incorrect because the program was unable to recognize "
								+ "your answer as one of these types.<br/><br/>");
					}
					buf.append("The answer submitted was: <b>" + answer + "</b><br/><br/>");
					break;
				case 6:  // Five-star rating submission
					buf.append("<h3>Thank you for the rating.</h3>");
					buf.append(q.printAllToStudents(answer) + "<br/><br/>");
					break;
				case 7:  // Essay question
					if (answer.length()>800) answer = answer.substring(0,799);
					JsonObject api_request = new JsonObject();  // these are used to score essay questions using ChatGPT
					api_request.addProperty("model","gpt-4");
					api_request.addProperty("max_tokens",200);
					api_request.addProperty("temperature",0.2);
					JsonObject m = new JsonObject();  // api request message
					m.addProperty("role", "user");
					String prompt = "Your role is a chemistry professor grading student exam questions. "
							+ "Question: \"" + q.text +  "\"\n Student Response: \"" + answer + "\"\n "
							+ "Using JSON format, provide a 'score' for this response on a scale of 0-5 and give "
							+ "'feedback' for how to improve the response, but do not reveal the correct response.";
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
						
					BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
					JsonObject api_response = JsonParser.parseReader(reader).getAsJsonObject();
					reader.close();
					
					// get the ChatGPT score from the response:
					try {
						try {
							String content = api_response.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
							JsonObject api_score = JsonParser.parseString(content).getAsJsonObject();
							int score = api_score.get("score").getAsInt();
							switch (score) {
							case 0:
							case 1: 
								buf.append("<h3>Your answer is incorrect.</h3>");
								break;
							case 2:
							case 3:
								buf.append("<h3>Your answer is partly correct, but needs improvement.</h3>");
								break;
							case 4:
							case 5:
								buf.append("<h3>Congratulations. You answered the question correctly.</h3>");
								break;
								}
							answer += "<br/><br/><b>Feedback: </b>" + api_score.get("feedback").getAsString() 
									+ "<br/><br/><b>Score: </b>" + score + "/5" + "<br/>";
							buf.append(q.printAllToStudents(answer) + "<br/>");
						} catch (Exception e) {
							buf.append("<h3>Oops, an error occurred. Please <a href=/Feedback>report a problem</a> with this question.</h3>" 
									+ (e.getMessage()==null?e.toString():e.getMessage()) + "<br/>");
							buf.append(api_response.toString() + "<br/><br/>");
						}
						break;
					} catch (Exception e) {}
					break;
				default:  // All other types of questions
					buf.append("<h3>Your answer was not correct. Please <a href=/item?q=" + q.id + ">try again</a>.</h3>");
					buf.append("The answer submitted was: <b>" + answer + "</b><br/><br/>");
				}
			}
			List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("conceptId",q.conceptId).keys().list();
			int i = new Random().nextInt(questionKeys.size());
			long questionId = questionKeys.get(i).getId();
			buf.append("<a class=btn href='/item?q=" + questionId + "'>Try another question on this concept.</a>");
			buf.append("&nbsp;&nbsp;");
			buf.append("<a href=/>Learn more about ChemVantage here</a><br/><br/>");
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
	
	String ajaxJavaScript(String signature) {
		return "<SCRIPT TYPE='text/javascript'>\n"
				+ "function ajaxSubmit(url,id,params,studentAnswer,note,email) {\n"
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
				+ "      '<FONT COLOR=RED><b>Thank you. An editor will review your comment.</b></FONT><p>';\n"
				+ "    }\n"
				+ "  }\n"
				+ "  url += '&QuestionId=' + id + '&Params=' + params + '&sig=" + signature + "&Notes=' + note + '&Email=' + email + '&StudentAnswer=' + studentAnswer;\n"
				+ "  xmlhttp.open('GET',url,true);\n"
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
	
	String orderResponses(String[] answers) {
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}

}
