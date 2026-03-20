package org.leoric.expensetracker.auth.services.interfaces;

import org.leoric.expensetracker.auth.dto.UserInfoResponse;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.models.User;
import org.springframework.stereotype.Service;

@Service
public interface UserService {
	UserInfoResponse getCurrentUser(User currentUser);

	UserResponseFullDto updateProfile(User currentUser, UserProfileUpdateDto dto);

	void changePassword(User currentUser, UserPasswordChangeDto dto);
}