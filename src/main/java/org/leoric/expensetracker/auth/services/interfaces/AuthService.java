package org.leoric.expensetracker.auth.services.interfaces;

import org.leoric.expensetracker.auth.dto.AuthenticationRequest;
import org.leoric.expensetracker.auth.dto.AuthenticationResponse;
import org.leoric.expensetracker.auth.dto.RegistrationRequest;
import org.leoric.expensetracker.auth.dto.UserInfoResponse;
import org.leoric.expensetracker.auth.models.User;
import org.springframework.stereotype.Service;

@Service
public interface AuthService {
	AuthenticationResponse register(RegistrationRequest request);

	AuthenticationResponse authenticate(AuthenticationRequest request);

	UserInfoResponse getCurrentUser(User user);
}