package ac.il.bgu.qa;

import ac.il.bgu.qa.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestAddRegister {

    private DatabaseService databaseService;
    private ReviewService reviewService;
    private NotificationService notificationService;
    private Library library;

    @BeforeEach
    void setUp() {
        databaseService = mock(DatabaseService.class);
        reviewService = mock(ReviewService.class);
        notificationService = mock(NotificationService.class);
        library = new Library(databaseService, reviewService);
    }

    @Test
    void addBook_nullBook_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, () -> library.addBook(null));
            assertEquals("Invalid book.", exception.getMessage());
    }
    @Test
    void addBook_validBook_addsBookToDatabase() {
        Book book = new Book("9780306406157", "Effective Testing", "John Smith");
        when(databaseService.getBookByISBN("9780306406157")).thenReturn(null);

        library.addBook(book);

        verify(databaseService).addBook("9780306406157", book);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "978030640615",
        "97803064061570",
        "9780306406158",
        "978030640615X",
        "978-030640615"
    })
    void addBook_invalidIsbn_throwsIllegalArgumentException(String isbn) {
        Book book = new Book(isbn, "Effective Testing", "John Smith");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, () -> library.addBook(book));
            assertEquals("Invalid ISBN.", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "9780306406157",
        "978-0-306-40615-7",
        "9783161484100",
        "9781408834015"
    })
    void addBook_validIsbn_addsBookToDatabase(String isbn) {
        Book book = new Book(isbn, "Effective Testing", "John Smith");

        library.addBook(book);

        verify(databaseService).addBook(isbn, book);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void addBook_nullOrEmptyTitle_throwsIllegalArgumentException(String title) {
        Book book = new Book("9780306406157", title, "John Smith");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, () -> library.addBook(book));
            assertEquals("Invalid title.", exception.getMessage());
    }

    @Test
    void addBook_bookAlreadyBorrowed_throwsIllegalArgumentException() {
        Book book = new Book("9780306406157", "Effective Testing", "John Smith");
        book.borrow();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Book with invalid borrowed state.", exception.getMessage());
    }

    @Test
    void addBook_bookAlreadyInDatabase_throwsIllegalArgumentException() {
        Book book = new Book("9780306406157", "Effective Testing", "John Smith");
        when(databaseService.getBookByISBN("9780306406157")).thenReturn(book);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Book already exists.", exception.getMessage());
        verify(databaseService, never()).addBook(any(), any());
    }

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
    void addBook_invalidAuthor_throwsIllegalArgumentException(String author) {
        Book book = new Book("9780306406157", "Effective Testing", author);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.addBook(book));
        assertEquals("Invalid author.", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Estee Cohen",
            "A",
            "Estee-Rose Cohen",
            "Estee O'Cohen",
            "E. R. Cohen",
            "a-'b"
    })
    void addBook_validAuthor_addsBookToDatabase(String author) {
        Book book = new Book("9780306406157", "Effective Testing", author);

        library.addBook(book);

        verify(databaseService).addBook("9780306406157", book);
    }

        @Test
    void registerUser_nullUser_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(null));
        assertEquals("Invalid user.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "12345678901",
            "1234567890123",
            "12345678901a",
            "a23456789012",
            "12345 678901"
    })
    void registerUser_invalidId_throwsIllegalArgumentException(String id) {
        User user = new User("Estee Cohen", id, notificationService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("Invalid user Id.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void registerUser_nullOrEmptyName_throwsIllegalArgumentException(String name) {
        User user = new User(name, "123456789012", notificationService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("Invalid user name.", exception.getMessage());
    }

    @Test
    void registerUser_nullNotificationService_throwsIllegalArgumentException() {
        User user = new User("Estee Cohen", "123456789012", null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("Invalid notification service.", exception.getMessage());
    }

    @Test
    void registerUser_userAlreadyExists_throwsIllegalArgumentException() {
        User user = new User("Estee Cohen", "123456789012", notificationService);
        when(databaseService.getUserById("123456789012")).thenReturn(user);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> library.registerUser(user));
        assertEquals("User already exists.", exception.getMessage());
        verify(databaseService, never()).registerUser(any(), any());
    }

    @Test
    void registerUser_validNewUser_registersUserInDatabase() {
        User user = new User("Estee Cohen", "123456789012", notificationService);

        library.registerUser(user);

        verify(databaseService).registerUser("123456789012", user);
    }

}