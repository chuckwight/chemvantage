/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2023 ChemVantage LLC
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

import com.google.cloud.tasks.v2.AppEngineHttpRequest;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Instant;

public class CreateTask {
  public static void init(String url,int seconds) throws Exception {
    // Instantiates a client.
    try (CloudTasksClient client = CloudTasksClient.create()) {
      // Variables provided by system variables.
      String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
      String queueName = System.getenv("QUEUE_ID");
      String location = System.getenv("LOCATION_ID");
      
      // Construct the fully qualified queue name.
      String queuePath = QueueName.of(projectId, location, queueName).toString();

       // Construct the task body.
      Task.Builder taskBuilder =
          Task.newBuilder()
              .setAppEngineHttpRequest(
                  AppEngineHttpRequest.newBuilder()
                      //.setBody(ByteString.copyFrom(payload, Charset.defaultCharset()))
                      .setRelativeUri("/tasks/create")
                      .setHttpMethod(HttpMethod.POST)
                      .build());

      // Add the scheduled time to the request.
      taskBuilder.setScheduleTime(
          Timestamp.newBuilder()
              .setSeconds(Instant.now(Clock.systemUTC()).plusSeconds(seconds).getEpochSecond()));

      // Send create task request.
      Task task = client.createTask(queuePath, taskBuilder.build());
      System.out.println("Task created: " + task.getName());
    }
  }
}