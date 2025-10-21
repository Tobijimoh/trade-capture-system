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


### Test Method: testSaveBook()
- **Problem:** The test `testSaveBook()` failed with a `NullPointerException` stating `"Cannot invoke 'com.technicalchallenge.mapper.BookMapper.toEntity(com.technicalchallenge.dto.BookDTO)' because 'this.bookMapper' is null"`. This occurred when `BookService.saveBook()` attempted to convert a `BookDTO` to a Book entity using the `bookMapper`.

- **Root Cause:** The `BookMapper` dependency in `BookService` was not mocked, causing the `bookMapper` field to be `null` during test execution. When the `saveBook()` method called `bookMapper.toEntity(dto)`, it triggered a `NullPointerException`.

- **Solution:** Added a `@Mock` for `BookMapper` in `BookServiceTest` and stubbed both `bookMapper.toEntity()` and `bookMapper.toDto()` methods within the `testSaveBook()` test:

```java
when(bookMapper.toEntity(bookDTO)).thenReturn(book);
when(bookRepository.save(book)).thenReturn(book);
when(bookMapper.toDto(book)).thenReturn(bookDTO);
```

This ensures proper mapping between `BookDTO` and `Book` during the save operation.

- **Impact:** The `testSaveBook()` test now executes successfully, confirming that `BookService.saveBook()` correctly saves a book entity and returns the expected `BookDTO` response.

### Test Method: testFindBookByNonExistentId()

- **Problem:** The test `testFindBookByNonExistentId()` previously failed with a `NullPointerException` due to the unmocked `BookMapper` dependency in `BookService`. 

- **Root Cause:** `BookMapper` was not mocked, causing a null reference when `BookService.getBookById()` attempted to map the book entity to a DTO. The test itself correctly tested a non-existent book, but the NPE blocked execution.

- **Solution:** By adding `BookMapper` and `CostCenterRepository` mocks in previous fixes, the test now runs successfully without any changes.

- **Impact:** `testFindBookByNonExistentId()` now passes, confirming that `BookService.getBookById()` correctly returns `Optional.empty()` for non-existent book IDs.


## Test Class: TradeServiceTest

### Test Method: testCreateTrade_Success()
- **Problem:** The `testCreateTrade_Success()` test failed with multiple errors while validating the trade creation workflow. Initially, the test threw a`RuntimeException` stating “Book not found or not set”, and after fixing reference data mocking, it later failed with a `NullPointerException` in the `TradeService.generateCashflows()` method.

- **Root Cause:** There were two main issues causing the failures:
1. Unmocked reference repositories — The `TradeService.createTrade()` method requires `Book`, `Counterparty`, and `TradeStatus` entities to be present when validating reference data. Since the test didn’t mock the corresponding repositories (`BookRepository`, `CounterpartyRepository`, and `TradeStatusRepository`), the method threw a `RuntimeException` during validation.

2. Unmocked TradeLegRepository.save() behavior —
`null`, causing a `NullPointerException` when `generateCashflows()` attempted to access `leg.getLegId()`.

- **Solution:** 
1. Added missing mocks for `BookRepository` and `CounterpartyRepository` at the top of the test class.
2. Updated the `testCreateTrade_Success()` setup to include valid test data for reference resolution:
```java
tradeDTO.setBookName("Book1");
tradeDTO.setCounterpartyName("Counterparty1");
tradeDTO.setTradeStatus("NEW");
```

3. Created mock entities for `Book`, `Counterparty`, and `TradeStatus`, and stubbed the repository methods:
```java
when(bookRepository.findByBookName(anyString())).thenReturn(Optional.of(mockBook));
when(counterpartyRepository.findByName(anyString())).thenReturn(Optional.of(mockCounterparty));
when(tradeStatusRepository.findByTradeStatus(anyString())).thenReturn(Optional.of(mockStatus));
```

4. Added a mock `TradeLeg` with a non-null `legId` and stubbed the save call to return it:
```java
when(tradeLegRepository.save(any(TradeLeg.class))).thenReturn(mockLeg);
```

This ensured the `generateCashflows()` method received a valid leg object and prevented the NPE.

- **Impact:** The `testCreateTrade_Success()` test now passes successfully, validating the entire trade creation workflow end-to-end. It confirms that:
    - Reference data (Book, Counterparty, TradeStatus) is correctly resolved.
    - Trade legs are created and persisted.
    - Cashflow generation logic executes without null pointer issues.

This fix stabilizes the trade creation process for future regression and integration testing.


### Test Method: testCreateTrade_InvalidDates_ShouldFail() 

- **Problem:** The test `testCreateTrade_InvalidDates_ShouldFail()` failed with an assertion error:
```java
expected: <Wrong error message> but was: <Start date cannot be before trade date>
indicating that the test was expecting an incorrect error message.
```
- **Root Cause:** The `TradeService.validateTradeCreation()` method correctly throws a `RuntimeException` with the message
"Start date cannot be before trade date" when the trade start date precedes the trade date.
However, the test was written with a placeholder string `"Wrong error message"`, making it intentionally fail.

- **Solution:** Updated the assertion in the test to check for the correct exception message:
```java
assertEquals("Start date cannot be before trade date", exception.getMessage());
```
- **Verification:** After updating the assertion, the test passes successfully, confirming that:
    - The TradeService.createTrade() method correctly validates trade dates.
    - An exception is thrown with the appropriate error message when the start date is before the trade date.