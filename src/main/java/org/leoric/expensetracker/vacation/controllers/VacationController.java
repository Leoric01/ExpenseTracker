package org.leoric.expensetracker.vacation.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.restaurant.services.RestaurantFileCleanupService;
import org.leoric.expensetracker.vacation.dtos.VacationDtos.*;
import org.leoric.expensetracker.vacation.services.VacationService;
import org.leoric.expensetracker.vacation.services.VacationConversionService;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@RestController @RequestMapping("/api/vacations/{trackerId}") @RequiredArgsConstructor
public class VacationController {
	private final VacationService service;
	private final VacationConversionService conversion;
	private final RestaurantFileCleanupService cleanup;
	private final ExpenseTrackerAccessService access;
	private void authorize(UUID trackerId, User user) { access.assertHasRoleOnExpenseTracker(trackerId, user, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER); }
	@GetMapping
	public Page<VacationSummary> list(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@RequestParam(required=false) String search, @RequestParam(required=false) String status,
			@RequestParam(required=false) String type, @RequestParam(required=false) String focus,
			@RequestParam(required=false) String country, @RequestParam(required=false) String provider,
			@RequestParam(required=false) String recommendation, @RequestParam(required=false) Integer year,
			@RequestParam(required=false) Integer minRating, @RequestParam(required=false) Integer maxRating,
			@RequestParam(defaultValue="date") String order, @RequestParam(defaultValue="0") int page,
			@RequestParam(defaultValue="20") int size) {
		authorize(trackerId,user); return service.list(trackerId,search,status,type,focus,country,provider,recommendation,year,minRating,maxRating,order,page,size);
	}
	@PostMapping public ResponseEntity<VacationDetail> create(@AuthenticationPrincipal User user, @PathVariable UUID trackerId, @Valid @RequestBody VacationInput in) {
		authorize(trackerId,user); return ResponseEntity.status(201).body(service.create(trackerId,in));
	}
	@GetMapping("/places") public List<Place> places(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@RequestParam(required=false) String search) {
		authorize(trackerId,user); return service.listPlaces(trackerId,search);
	}
	@GetMapping("/places/search") public Page<Place> searchPlaces(@AuthenticationPrincipal User user, @PathVariable UUID trackerId,
			@RequestParam(required=false) String search, @RequestParam(required=false) String kind,
			@RequestParam(required=false) String area, @RequestParam(required=false) String recommendation,
			@RequestParam(required=false) Integer minRating, @RequestParam(required=false) Integer maxRating,
			@RequestParam(defaultValue="name") String order, @RequestParam(defaultValue="0") int page,
			@RequestParam(defaultValue="20") int size) {
		authorize(trackerId,user);
		return service.searchPlaces(trackerId,search,kind,area,recommendation,minRating,maxRating,order,page,size);
	}
	@PostMapping("/places") public ResponseEntity<Place> createPlace(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@Valid @RequestBody PlaceInput in) {
		authorize(trackerId,user); return ResponseEntity.status(201).body(service.createPlace(trackerId,in));
	}
	@GetMapping("/places/{placeId}") public PlaceDetail place(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID placeId) {
		authorize(trackerId,user); return service.placeDetail(trackerId,placeId);
	}
	@PutMapping("/places/{placeId}") public Place updatePlace(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID placeId,@Valid @RequestBody PlaceInput in) {
		authorize(trackerId,user); return service.updatePlace(trackerId,placeId,in);
	}
	@DeleteMapping("/places/{placeId}") public ResponseEntity<Void> deletePlace(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID placeId) {
		authorize(trackerId,user); service.deletePlace(trackerId,placeId); return ResponseEntity.noContent().build();
	}
	@GetMapping("/{id}") public VacationDetail detail(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id) {
		authorize(trackerId,user); return service.detail(trackerId,id);
	}
	@GetMapping("/{id}/conversion") public VacationConversionService.Conversion conversion(@AuthenticationPrincipal User user,
			@PathVariable UUID trackerId, @PathVariable UUID id, @RequestParam String target,
			@RequestParam(defaultValue="historical") String mode) {
		authorize(trackerId,user); return conversion.convert(trackerId,id,target,mode);
	}
	@PutMapping("/{id}") public VacationDetail update(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@Valid @RequestBody VacationInput in) {
		authorize(trackerId,user); return service.update(trackerId,id,in);
	}
	@DeleteMapping("/{id}") public DeleteResult delete(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id) {
		authorize(trackerId,user); service.deleteVacation(trackerId,id); return new DeleteResult(cleanup.process());
	}
	@PostMapping("/{id}/visits") public ResponseEntity<Visit> addVisit(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@Valid @RequestBody VisitInput in) {
		authorize(trackerId,user); return ResponseEntity.status(201).body(service.createVisit(trackerId,id,in));
	}
	@PutMapping("/{id}/visits/{visitId}") public Visit updateVisit(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@PathVariable UUID visitId,@Valid @RequestBody VisitInput in) {
		authorize(trackerId,user); return service.updateVisit(trackerId,id,visitId,in);
	}
	@DeleteMapping("/{id}/visits/{visitId}") public DeleteResult deleteVisit(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@PathVariable UUID visitId) {
		authorize(trackerId,user); service.deleteVisit(trackerId,id,visitId); return new DeleteResult(cleanup.process());
	}
	@PostMapping("/{id}/expenses") public ResponseEntity<Expense> addExpense(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@Valid @RequestBody ExpenseInput in) {
		authorize(trackerId,user); return ResponseEntity.status(201).body(service.createExpense(trackerId,id,in));
	}
	@PutMapping("/{id}/expenses/{expenseId}") public Expense updateExpense(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@PathVariable UUID expenseId,@Valid @RequestBody ExpenseInput in) {
		authorize(trackerId,user); return service.updateExpense(trackerId,id,expenseId,in);
	}
	@DeleteMapping("/{id}/expenses/{expenseId}") public ResponseEntity<Void> deleteExpense(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@PathVariable UUID expenseId) {
		authorize(trackerId,user); service.deleteExpense(trackerId,id,expenseId); return ResponseEntity.noContent().build();
	}
	@PostMapping(value="/{id}/photos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Photo> addPhoto(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@RequestParam(required=false) UUID visitId,@RequestParam MultipartFile file) {
		authorize(trackerId,user); return ResponseEntity.status(201).body(service.addPhoto(trackerId,id,visitId,file));
	}
	@DeleteMapping("/{id}/photos/{photoId}") public DeleteResult deletePhoto(@AuthenticationPrincipal User user,@PathVariable UUID trackerId,@PathVariable UUID id,@PathVariable UUID photoId) {
		authorize(trackerId,user); service.deletePhoto(trackerId,id,photoId); return new DeleteResult(cleanup.process());
	}
	@PostMapping("/cleanup") public DeleteResult cleanup(@AuthenticationPrincipal User user,@PathVariable UUID trackerId) {
		authorize(trackerId,user); return new DeleteResult(cleanup.process());
	}
}
