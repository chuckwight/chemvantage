package org.chemvantage;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.googlecode.objectify.ObjectifyService;

@WebListener
public class ObjectifyWebListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent event) {
    ObjectifyService.init();
    // This is a good place to register your POJO entity classes.
    ObjectifyService.register(Assignment.class);
    ObjectifyService.register(BLTIConsumer.class);
    ObjectifyService.register(Deployment.class);
    ObjectifyService.register(Domain.class);
    ObjectifyService.register(Group.class);
    ObjectifyService.register(HWTransaction.class);
    ObjectifyService.register(Nonce.class);
    ObjectifyService.register(PracticeExamTransaction.class);
    ObjectifyService.register(ProposedQuestion.class);
    ObjectifyService.register(Question.class);
    ObjectifyService.register(QuizTransaction.class);
    ObjectifyService.register(Response.class);
    ObjectifyService.register(Score.class);
    ObjectifyService.register(Subject.class);
    ObjectifyService.register(Text.class);
    ObjectifyService.register(Topic.class);
    ObjectifyService.register(User.class);
    ObjectifyService.register(UserReport.class);
    ObjectifyService.register(Video.class);
    
    ObjectifyService.begin();
	
  }
	
  @Override
  public void contextDestroyed(ServletContextEvent event) {
  }
}