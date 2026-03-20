package org.leoric.expensetracker.auth.services.interfaces;

import org.leoric.expensetracker.auth.dto.AuthenticationRequestDto;
import org.leoric.expensetracker.auth.dto.AuthenticationResponseDto;
import org.leoric.expensetracker.auth.dto.RegistrationRequestDto;
import org.springframework.stereotype.Service;

@Service
public interface AuthService {
	void authRegister(RegistrationRequestDto request);

	AuthenticationResponseDto authLogin(AuthenticationRequestDto request);
}