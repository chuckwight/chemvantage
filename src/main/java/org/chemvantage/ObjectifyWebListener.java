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
    ObjectifyService.register(Concept.class);
    ObjectifyService.register(Contact.class);
    ObjectifyService.register(Deployment.class);
    ObjectifyService.register(EmailMessage.class);
    ObjectifyService.register(Group.class);
    ObjectifyService.register(HWTransaction.class);
    ObjectifyService.register(Nonce.class);
    ObjectifyService.register(PollTransaction.class);
    ObjectifyService.register(PlacementExamTransaction.class);
    ObjectifyService.register(PracticeExamTransaction.class);
    ObjectifyService.register(PremiumUser.class);
    ObjectifyService.register(ProposedQuestion.class);
    ObjectifyService.register(Question.class);
    ObjectifyService.register(QuizTransaction.class);
    ObjectifyService.register(Response.class);
    ObjectifyService.register(RSAKeyPair.class);
    ObjectifyService.register(Score.class);
    ObjectifyService.register(STTransaction.class);
    ObjectifyService.register(Subject.class);
    ObjectifyService.register(Text.class);
    ObjectifyService.register(Topic.class);
    ObjectifyService.register(User.class);
    ObjectifyService.register(UserReport.class);
    ObjectifyService.register(Video.class);
    ObjectifyService.register(VideoTransaction.class);
    ObjectifyService.register(Voucher.class);
    
    ObjectifyService.begin();
	
  }
	
  @Override
  public void contextDestroyed(ServletContextEvent event) {
  }
}