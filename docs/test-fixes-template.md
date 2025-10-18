# Test Fixes
___

## Test Class: TradeControllerTest

### Test Method: testCreateTrade

- **Problem:** The test expected HTTP 200 OK, but the controller correctly returned 201 Created for successful resource creation.
- **Root Cause:** Incorrect expectation in the test assertion.
- **Solution:** Updated the test to expect 201 Created instead of 200 OK.
- **Impact:** The test now properly verifies REST-compliant trade creation responses and passes successfully.


### Test Method: testCreateTradeValidationFailure_MissingTradeDate

- **Problem:** The test was failing because the controller did not return the expected validation error when `tradeDate` was missing in the trade creation request.
- **Root Cause:** The controller and DTO were not correctly enforcing input validation - `@Valid` was missing from the controller, and validation annotations were using `javax.validation` instead of `jakarta.validation`, which caused validation to be silently ignored in Spring Boot 3+.
- **Solution:** Added `@Valid` to the `createTrade()` method parameter, updated DTO field annotations to use `jakarta.validation`, and implemented a `GlobalExceptionHandler` to return clear validation messages.
- **Impact:** Validation now triggers properly during trade creation, invalid inputs return a 400 Bad Request with descriptive messages, and the test passes successfully.


### Test Method: testCreateTradeValidationFailure_MissingBook

- **Problem:** The test failed because the controller returned 201 Created when a trade with a missing book name was submitted, instead of the expected 400 Bad Request.
- **Root Cause:** `TradeDTO.bookName` did not have a validation annotation, so Spring’s `@Valid` did not trigger a validation error.
- **Solution:** Added `@NotBlank(message = "Book and Counterparty are required")` to `TradeDTO.bookName`.
- **Impact:** Ensures proper validation of trade creation requests and makes the test pass successfully.


### Test Method: testUpdateTrade
- **Problem:** The test failed with an `AssertionError` stating “No value at JSON path `$.tradeId`,” indicating the response body was empty when asserting the updated trade response.
- **Root Cause:** The controller method `updateTrade()` calls `tradeService.amendTrade()`, but the test incorrectly mocked `tradeService.saveTrade()`. As a result, the mock returned null, leading to an empty response body and failed JSON path assertion.
- **Solution:**  Updated the test to mock `tradeService.amendTrade()` instead of `saveTrade()` and ensured the `tradeMapper.toDto()` method was also mocked to return the expected `TradeDTO`.
- **Impact:** The test now correctly validates that the update endpoint (`PUT /api/trades/{id}`) returns a populated `TradeDTO` with the correct `tradeId`, confirming successful trade update behavior.


### Test Method: testUpdateTradeIdMismatch

- **Problem:** The test failed because the controller allowed updates even when the Trade ID in the path differed from the Trade ID in the request body.
- **Root Cause:** There was no explicit validation in `updateTrade()` to check for ID mismatch.
- **Solution:** Added a check at the start of `updateTrade()` to return a 400 Bad Request when tradeDTO.tradeId does not match the path ID.
- **Impact:** Ensures consistency of trade updates and enables the test to pass successfully.


### Test Method: testDeleteTrade
- **Problem:** The test expected HTTP 204 No Content when deleting a trade, but the controller returned 200 OK.
- **Root Cause:** The controller’s `deleteTrade()` method returns `ResponseEntity.ok().body("Trade cancelled successfully")` instead of `ResponseEntity.noContent()`.
- **Solution:** Updated the `deleteTrade()` to return `return ResponseEntity.noContent()` Updated the `@ApiResponse(responseCode = "200", …)` annotation to `responseCode = "204"` to match REST conventions..
- **Impact:** The test now correctly verifies trade deletion behavior and passes successfully.


## Test Class: BookServiceTest

### Test Method: testFindBookById()
- **Problem:** The test `testFindBookById()` failed with a `NullPointerException` stating `"Cannot invoke 'Object.getClass()' because 'this.bookMapper' is null"`.
This occurred when `BookService.getBookById()` attempted to call `bookMapper.toDto()` during the test.

- **Root Cause:** The `BookMapper` dependency was not mocked in the test. When the service method executed, the `bookMapper` field inside `BookService` was `null`, resulting in a `NullPointerException`.

- **Solution:** Added a `@Mock` for `BookMapper` in `BookServiceTest` and stubbed its behavior within `testFindBookById()` using `when(bookMapper.toDto(book)).thenReturn(dto);`. This ensures that the `BookService.getBookById()` call now returns a valid BookDTO when the repository finds a book.
- **Impact:** The `testFindBookById()` test now runs successfully, verifying that `BookService.getBookById()` correctly retrieves and maps a `Book` entity to a `BookDTO`. This confirms the basic retrieval flow works as intended.
