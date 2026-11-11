package ac.il.bgu.qa;

import ac.il.bgu.qa.errors.BookAlreadyBorrowedException;
import ac.il.bgu.qa.errors.BookNotFoundException;
import ac.il.bgu.qa.errors.BookNotBorrowedException;
import ac.il.bgu.qa.errors.NoReviewsFoundException;
import ac.il.bgu.qa.errors.NotificationException;
import ac.il.bgu.qa.errors.ReviewException;
import ac.il.bgu.qa.errors.ReviewServiceUnavailableException;
import ac.il.bgu.qa.errors.UserNotRegisteredException;
import ac.il.bgu.qa.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Library class.
 * The Library is isolated from its collaborators by mocking the services it uses
 * (DatabaseService, ReviewService) and the NotificationService held by each User.
 * Test names follow the course convention: Given<Condition>_When<Method>_Then<Result>.
 */
public class TestLibrary {

    // A valid ISBN-13 (check digit 7).
    private static final String VALID_ISBN = "9780306406157";
    private static final String VALID_TITLE = "Effective Testing";
    private static final String VALID_AUTHOR = "Estee Cohen";
    private static final String VALID_USER_ID = "123456789012";
    private static final String VALID_USER_NAME = "Estee Cohen";

    private DatabaseService databaseService;
    private ReviewService reviewService;
    private NotificationService notificationService;
    private Library library;

    /**
     * Creates fresh mocks and a new Library before every test,
     * so that no test depends on the state left by another one.
     */
    @BeforeEach
    void setUp() {
        databaseService = mock(DatabaseService.class);
        reviewService = mock(ReviewService.class);
        notificationService = mock(NotificationService.class);
        library = new Library(databaseService, reviewService);
    }

    // ------------------------------------------------------------------
    // addBook
    // ------------------------------------------------------------------

    @Test
    void GivenNullBook_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(null));
        assertEquals("Invalid book.", exception.getMessage());
    }

    @Test
    void GivenValidBook_WhenAddBook_ThenAddBookToDatabase() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    /**
     * Each value breaks exactly one ISBN rule: null, empty, too short, too long,
     * wrong check digit, a non-digit character, and too short after hyphen removal.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "978030640615",
            "97803064061570",
            "9780306406158",
            "978030640615X",
            "978-030640615"
    })
    void GivenInvalidIsbn_WhenAddBook_ThenThrowIllegalArgumentException(String isbn) {
        // Arrange
        Book book = new Book(isbn, VALID_TITLE, VALID_AUTHOR);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    /**
     * Covers a plain ISBN, an ISBN with hyphens, an ISBN whose calculated
     * check digit is 10 (and therefore becomes 0), and an ISBN with another check digit.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "9780306406157",
            "978-0-306-40615-7",
            "9783161484100",
            "9781408834015"
    })
    void GivenValidIsbn_WhenAddBook_ThenAddBookToDatabase(String isbn) {
        // Arrange
        Book book = new Book(isbn, VALID_TITLE, VALID_AUTHOR);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(isbn, book);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void GivenNullOrEmptyTitle_WhenAddBook_ThenThrowIllegalArgumentException(String title) {
        // Arrange
        Book book = new Book(VALID_ISBN, title, VALID_AUTHOR);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Invalid title.", exception.getMessage());
    }

    /**
     * Each value breaks exactly one author rule: null, empty, a bad first character,
     * a bad last character, a forbidden character inside, and each kind of
     * consecutive special characters (space, hyphen, apostrophe, dot).
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            ".Estee",
            "1Estee",
            "Estee.",
            "Estee1",
            "Es1tee",
            "Es@tee",
            "Estee  Cohen",
            "Estee--Cohen",
            "Estee''Cohen",
            "Estee..Cohen"
    })
    void GivenInvalidAuthor_WhenAddBook_ThenThrowIllegalArgumentException(String author) {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, author);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Invalid author.", exception.getMessage());
    }

    /**
     * Covers a regular name, a single letter, every allowed special character,
     * and two different special characters in a row (which is allowed).
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Estee Cohen",
            "A",
            "Estee-Rose Cohen",
            "Estee O'Cohen",
            "E. R. Cohen",
            "a-'b"
    })
    void GivenValidAuthor_WhenAddBook_ThenAddBookToDatabase(String author) {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, author);

        // Act
        library.addBook(book);

        // Assert
        verify(databaseService).addBook(VALID_ISBN, book);
    }

    @Test
    void GivenBorrowedBook_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        book.borrow();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Book with invalid borrowed state.", exception.getMessage());
    }

    @Test
    void GivenBookAlreadyInDatabase_WhenAddBook_ThenThrowIllegalArgumentException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Book already exists.", exception.getMessage());
        verify(databaseService, never()).addBook(any(), any());
    }

    // ------------------------------------------------------------------
    // registerUser
    // ------------------------------------------------------------------

    @Test
    void GivenNullUser_WhenRegisterUser_ThenThrowIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(null));
        assertEquals("Invalid user.", exception.getMessage());
    }

    /**
     * A valid user Id is exactly 12 digits. The values are null, empty,
     * one digit too short, one digit too long, and 12 characters that contain
     * a letter at the end, a letter at the start, or a space.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "12345678901",
            "1234567890123",
            "12345678901a",
            "a23456789012",
            "12345 678901"
    })
    void GivenInvalidId_WhenRegisterUser_ThenThrowIllegalArgumentException(String id) {
        // Arrange
        User user = new User(VALID_USER_NAME, id, notificationService);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void GivenNullOrEmptyName_WhenRegisterUser_ThenThrowIllegalArgumentException(String name) {
        // Arrange
        User user = new User(name, VALID_USER_ID, notificationService);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("Invalid user name.", exception.getMessage());
    }

    @Test
    void GivenNullNotificationService_WhenRegisterUser_ThenThrowIllegalArgumentException() {
        // Arrange
        User user = new User(VALID_USER_NAME, VALID_USER_ID, null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("Invalid notification service.", exception.getMessage());
    }

    @Test
    void GivenUserAlreadyExists_WhenRegisterUser_ThenThrowIllegalArgumentException() {
        // Arrange
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("User already exists.", exception.getMessage());
        verify(databaseService, never()).registerUser(any(), any());
    }

    @Test
    void GivenValidNewUser_WhenRegisterUser_ThenRegisterUserInDatabase() {
        // Arrange
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);

        // Act
        library.registerUser(user);

        // Assert
        verify(databaseService).registerUser(VALID_USER_ID, user);
    }
    // ------------------------------------------------------------------
    // BorrowBook
    // ------------------------------------------------------------------


    @Test
    void GivenValidBook_WhenBorrowBook_ThenBorrowBook() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act
        library.borrowBook(VALID_ISBN,VALID_USER_ID);

        // Assert
        assertEquals(true,book.isBorrowed());
    }

    @Test
    void GivenValidBook_WhenBorrowBook_ThenBorrowBookInDatabase () {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act
        library.borrowBook(VALID_ISBN,VALID_USER_ID);

        //Assert
        verify(databaseService).borrowBook(VALID_ISBN, VALID_USER_ID);

    }

    /**
     * Covers a plain ISBN, an ISBN with hyphens, an ISBN whose calculated
     * check digit is 10 (and therefore becomes 0), and an ISBN with another check digit.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "9780306406158",
            "978-0-306-40615-8",
            "9783161484101",
            "9781408834016"
    })
    void GivenInvalidIsbn_WhenBorrowBook_ThenThrowIllegalArgumentException (String isbn) {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.borrowBook(isbn,VALID_USER_ID));
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @Test
    void GivenNoBook_WhenBorrowBook_ThenThrowBookNotFoundException () {
        // Arrange
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        library.registerUser(user);

        // Act & Assert
        BookNotFoundException exception = assertThrows(
                BookNotFoundException.class, () -> library.borrowBook(VALID_ISBN,VALID_USER_ID));
        assertEquals("Book not found!",exception.getMessage());

    }

    /**
     * A valid user Id is exactly 12 digits. The values are null, empty,
     * one digit too short, one digit too long, and 12 characters that contain
     * a letter at the end, a letter at the start, or a space.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "12345678901",
            "1234567890123",
            "12345678901a",
            "a23456789012",
            "12345 678901"
    })
    void GivenInvalidUserId_WhenBorrowBook_ThenThrowIllegalArgumentException (String id) {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.borrowBook(VALID_ISBN,id));
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @Test
    void GivenValidUnregisteredUserId_WhenBorrowBook_ThenThrowUserNotRegisteredException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        // Act & Assert
        UserNotRegisteredException exception = assertThrows(
                UserNotRegisteredException.class, () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));
        assertEquals("User not found!", exception.getMessage());
    }

    @Test
    void GivenAlreadyBorrowedBook_WhenBorrowBook_ThenThrowBookAlreadyBorrowedException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        book.borrow();
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);

        // Act & Assert
        BookAlreadyBorrowedException exception = assertThrows(
                BookAlreadyBorrowedException.class, () -> library.borrowBook(VALID_ISBN, VALID_USER_ID));
        assertEquals("Book is already borrowed!", exception.getMessage());
    }

    // ------------------------------------------------------------------
    // returnBook
    // ------------------------------------------------------------------

    @Test
    void GivenBorrowedBook_WhenReturnBook_ThenMarkBookAsReturned() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        book.borrow();
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        library.returnBook(VALID_ISBN);

        // Assert
        assertFalse(book.isBorrowed());
    }

    @Test
    void GivenInvalidIsbn_WhenReturnBook_ThenThrowIllegalArgumentException() {
        // Arrange
        String invalidIsbn = "9780306406158";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.returnBook(invalidIsbn));
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @Test
    void GivenNoBook_WhenReturnBook_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act & Assert
        BookNotFoundException exception = assertThrows(
                BookNotFoundException.class, () -> library.returnBook(VALID_ISBN));
        assertEquals("Book not found!", exception.getMessage());
    }

    @Test
    void GivenBookNotBorrowed_WhenReturnBook_ThenThrowBookNotBorrowedException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act & Assert
        BookNotBorrowedException exception = assertThrows(
                BookNotBorrowedException.class, () -> library.returnBook(VALID_ISBN));
        assertEquals("Book wasn't borrowed!", exception.getMessage());
    }

    @Test
    void GivenBorrowedBook_WhenReturnBook_ThenReturnBookInDatabase() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        book.borrow();
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act
        library.returnBook(VALID_ISBN);

        // Assert
        verify(databaseService).returnBook(VALID_ISBN);
    }

    // ------------------------------------------------------------------
    // notifyUserWithBookReviews
    // ------------------------------------------------------------------

    @Test
    void GivenBookWithReviews_WhenNotifyUserWithBookReviews_ThenSendReviewsToUser() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN))
                .thenReturn(Arrays.asList("Clear examples.", "Easy to follow."));

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(notificationService).notifyUser(VALID_USER_ID,
                "Reviews for '" + VALID_TITLE + "':\nClear examples.\nEasy to follow.");
    }

    @Test
    void GivenInvalidUserId_WhenNotifyUserWithBookReviews_ThenThrowIllegalArgumentException() {
        // Arrange
        String invalidUserId = "12345678901";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, invalidUserId));
        assertEquals("Invalid user Id.", exception.getMessage());
        verifyNoInteractions(databaseService, reviewService);
    }

    @Test
    void GivenInvalidIsbn_WhenNotifyUserWithBookReviews_ThenThrowIllegalArgumentException() {
        // Arrange
        String invalidIsbn = "9780306406158";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.notifyUserWithBookReviews(invalidIsbn, VALID_USER_ID));
        assertEquals("Invalid ISBN.", exception.getMessage());
        verifyNoInteractions(databaseService, reviewService);
    }

    @Test
    void GivenValidUnregisteredUserId_WhenNotifyUserWithBookReviews_ThenThrowUserNotRegisteredException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(null);

        // Act & Assert
        UserNotRegisteredException exception = assertThrows(UserNotRegisteredException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));
        assertEquals("User not found!", exception.getMessage());
    }

    @Test
    void GivenNoBook_WhenNotifyUserWithBookReviews_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act & Assert
        BookNotFoundException exception = assertThrows(BookNotFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));
        assertEquals("Book not found!", exception.getMessage());
    }

    @Test
    void GivenNoReviews_WhenNotifyUserWithBookReviews_ThenThrowNoReviewsFoundException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Collections.emptyList());

        // Act & Assert
        NoReviewsFoundException exception = assertThrows(NoReviewsFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));
        assertEquals("No reviews found!", exception.getMessage());
    }

    @Test
    void GivenNullReviews_WhenNotifyUserWithBookReviews_ThenThrowNoReviewsFoundException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(null);

        // Act & Assert
        NoReviewsFoundException exception = assertThrows(NoReviewsFoundException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));
        assertEquals("No reviews found!", exception.getMessage());
        verifyNoInteractions(notificationService);
    }

    @Test
    void GivenReviewServiceFailure_WhenNotifyUserWithBookReviews_ThenThrowReviewServiceUnavailableException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenThrow(new ReviewException("Fetch failed."));

        // Act & Assert
        ReviewServiceUnavailableException exception = assertThrows(ReviewServiceUnavailableException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));
        assertEquals("Review service unavailable!", exception.getMessage());
        verify(reviewService).close();
        verifyNoInteractions(notificationService);
    }

    @Test
    void GivenFourNotificationFailures_WhenNotifyUserWithBookReviews_ThenSucceedOnFifthAttempt() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Collections.singletonList("Clear examples."));
        NotificationException failure = new NotificationException("Delivery failed.");
        doThrow(failure, failure, failure, failure).doNothing()
                .when(notificationService).notifyUser(any(), any());

        // Act
        library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID);

        // Assert
        verify(notificationService, times(5)).notifyUser(any(), any());
    }

    @Test
    void GivenFiveNotificationFailures_WhenNotifyUserWithBookReviews_ThenThrowNotificationException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Collections.singletonList("Clear examples."));
        doThrow(new NotificationException("Delivery failed."))
                .when(notificationService).notifyUser(any(), any());

        // Act & Assert
        NotificationException exception = assertThrows(NotificationException.class,
                () -> library.notifyUserWithBookReviews(VALID_ISBN, VALID_USER_ID));
        assertEquals("Notification failed!", exception.getMessage());
        verify(notificationService, times(5)).notifyUser(any(), any());
    }

    // ------------------------------------------------------------------
    // getBookByISBN
    // ------------------------------------------------------------------

    @Test
    void GivenAvailableBook_WhenGetBookByISBN_ThenReturnBookAndNotifyUser() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Collections.singletonList("Clear examples."));

        // Act
        Book result = library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
        verify(notificationService).notifyUser(VALID_USER_ID, "Reviews for '" + VALID_TITLE + "':\nClear examples.");
    }

    @Test
    void GivenInvalidIsbn_WhenGetBookByISBN_ThenThrowIllegalArgumentException() {
        // Arrange
        String invalidIsbn = "9780306406158";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.getBookByISBN(invalidIsbn, VALID_USER_ID));
        assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @Test
    void GivenInvalidUserId_WhenGetBookByISBN_ThenThrowIllegalArgumentException() {
        // Arrange
        String invalidUserId = "12345678901";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> library.getBookByISBN(VALID_ISBN, invalidUserId));
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @Test
    void GivenNoBook_WhenGetBookByISBN_ThenThrowBookNotFoundException() {
        // Arrange
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(null);

        // Act & Assert
        BookNotFoundException exception = assertThrows(BookNotFoundException.class,
                () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));
        assertEquals("Book not found!", exception.getMessage());
    }

    @Test
    void GivenAlreadyBorrowedBook_WhenGetBookByISBN_ThenThrowBookAlreadyBorrowedException() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        book.borrow();
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);

        // Act & Assert
        BookAlreadyBorrowedException exception = assertThrows(BookAlreadyBorrowedException.class,
                () -> library.getBookByISBN(VALID_ISBN, VALID_USER_ID));
        assertEquals("Book was already borrowed!", exception.getMessage());
    }

    @Test
    void GivenNotificationFailure_WhenGetBookByISBN_ThenStillReturnBook() {
        // Arrange
        Book book = new Book(VALID_ISBN, VALID_TITLE, VALID_AUTHOR);
        when(databaseService.getBookByISBN(VALID_ISBN)).thenReturn(book);
        User user = new User(VALID_USER_NAME, VALID_USER_ID, notificationService);
        when(databaseService.getUserById(VALID_USER_ID)).thenReturn(user);
        when(reviewService.getReviewsForBook(VALID_ISBN)).thenReturn(Collections.singletonList("Clear examples."));
        doThrow(new NotificationException("Delivery failed."))
                .when(notificationService).notifyUser(any(), any());

        // Act
        Book result = library.getBookByISBN(VALID_ISBN, VALID_USER_ID);

        // Assert
        assertSame(book, result);
        verify(notificationService, atLeastOnce()).notifyUser(any(), any());
    }

}

