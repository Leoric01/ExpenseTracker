package org.leoric.expensetracker.auth.services.interfaces;

import org.leoric.expensetracker.auth.dto.UserInfoResponseDto;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public interface UserService {
	UserInfoResponseDto profileMe(User currentUser);

	UserResponseFullDto profileUpdate(User currentUser, UserProfileUpdateDto dto);

	void profileChangePassword(User currentUser, UserPasswordChangeDto dto);

	Page<UserResponseFullDto> profileFindAllPageable(String search, Pageable pageable);

	void profileDeleteMe(User currentUser);
}