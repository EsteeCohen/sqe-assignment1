package ac.il.bgu.qa;

import ac.il.bgu.qa.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
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
}