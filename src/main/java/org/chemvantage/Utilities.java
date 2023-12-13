package org.chemvantage;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Instant;

import com.google.cloud.tasks.v2.AppEngineHttpRequest;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

public class Utilities {
	
	public static void createTask(String relativeUri, String query) throws IOException {
		createTask(relativeUri, query, 0);
	}
	
	public static void createTask(String relativeUri, String query, int seconds)
			throws IOException {
		// This method accepts a relativeUri (e.g., /ReportScore) to POST a request to a ChemVantage servlet
		String projectId = Subject.projectId;
		String location = "us-central1";
		String queueName = "default";
		// Instantiates a client.
		try (CloudTasksClient client = CloudTasksClient.create()) {
			// Construct the fully qualified queue name.
			String queuePath = QueueName.of(projectId, location, queueName).toString();

			// Build the Task:
			Task.Builder taskBuilder =
					Task.newBuilder()
					.setAppEngineHttpRequest(
							AppEngineHttpRequest.newBuilder()
							.setBody(ByteString.copyFrom(query, Charset.defaultCharset()))
							.setRelativeUri(relativeUri)
							.setHttpMethod(HttpMethod.POST)
							.putHeaders("Content-Type", "application/x-www-form-urlencoded")
							.build());

			// Add the scheduled time to the request.
			taskBuilder.setScheduleTime(
					Timestamp.newBuilder()
					.setSeconds(Instant.now(Clock.systemUTC()).plusSeconds(seconds).getEpochSecond()));

			// Send create task request.
			client.createTask(queuePath, taskBuilder.build());  // returns Task entity
			//System.out.println("Task created: " + task.getName());
		}
	}
	
	public static void sendEmail(String recipientName, String recipientEmail, String subject, String message) 
			throws IOException {
		Email from = new Email("admin@chemvantage.org","ChemVantage LLC");
		if (recipientName==null) recipientName="";
		Email to = new Email(recipientEmail,recipientName);
		Content content = new Content("text/html", message);
		Mail mail = new Mail(from, subject, to, content);

		SendGrid sg = new SendGrid(Subject.getSendGridKey());
		Request request = new Request();
		request.setMethod(Method.POST);
		request.setEndpoint("mail/send");
		request.setBody(mail.build());
		Response response = sg.api(request);
		System.out.println(response.getStatusCode());
		System.out.println(response.getBody());
		System.out.println(response.getHeaders());
	}
}