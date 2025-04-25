package com.dulno.access.portal.verification;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationSpringConfiguration {
  @Autowired
  private SessionCloseSchedule sessionCloseSchedule;
  @Autowired
  private SignupWithdrawSchedule signupWithdrawSchedule;

  @PostConstruct
  private void startSessionCloseSchedule() throws Exception {
    sessionCloseSchedule.start();
  }

  @PostConstruct
  private void startSignupWithdrawSchedule() throws Exception {
    signupWithdrawSchedule.start();
  }
}