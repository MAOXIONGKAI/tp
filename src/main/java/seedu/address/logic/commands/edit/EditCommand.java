package seedu.address.logic.commands.edit;

import static java.util.Objects.requireNonNull;
import static seedu.address.logic.parser.CliSyntax.PREFIX_ADDRESS;
import static seedu.address.logic.parser.CliSyntax.PREFIX_DESCRIPTION;
import static seedu.address.logic.parser.CliSyntax.PREFIX_EMAIL;
import static seedu.address.logic.parser.CliSyntax.PREFIX_MODULE;
import static seedu.address.logic.parser.CliSyntax.PREFIX_NAME;
import static seedu.address.logic.parser.CliSyntax.PREFIX_PHONE;
import static seedu.address.logic.parser.CliSyntax.PREFIX_TAG;
import static seedu.address.model.Model.PREDICATE_SHOW_ALL_PERSONS;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import seedu.address.commons.core.index.Index;
import seedu.address.commons.util.CollectionUtil;
import seedu.address.commons.util.ToStringBuilder;
import seedu.address.logic.Messages;
import seedu.address.logic.commands.Command;
import seedu.address.logic.commands.CommandResult;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.model.Model;
import seedu.address.model.person.Address;
import seedu.address.model.person.Description;
import seedu.address.model.person.Email;
import seedu.address.model.person.ModuleRoleMap;
import seedu.address.model.person.Name;
import seedu.address.model.person.Person;
import seedu.address.model.person.Phone;
import seedu.address.model.tag.Tag;

/**
 * Edits the details of an existing person in the address book.
 */
public class EditCommand extends Command {

    public static final String COMMAND_WORD = "edit";

    public static final String MESSAGE_USAGE = COMMAND_WORD + ": Edits the details of the person identified "
            + "by the index number used in the displayed person list. "
            + "Existing values will be overwritten by the input values. (except for module roles)\n"
            + "When adding module roles, 'Student' is the default role type if you do not specify.\n"
            + "When deleting module roles, any role associated with the module will be deleted if you do not specify.\n"
            + "Parameters: INDEX (must be a positive integer) "
            + "[" + PREFIX_NAME + "NAME] "
            + "[" + PREFIX_PHONE + "PHONE] "
            + "[" + PREFIX_EMAIL + "EMAIL] "
            + "[" + PREFIX_ADDRESS + "ADDRESS] "
            + "[" + PREFIX_TAG + "TAG]+ "
            + "[" + PREFIX_MODULE + "(+ | -)(MODULECODE[-ROLETYPE])+] "
            + "[" + PREFIX_DESCRIPTION + "DESCRIPTION]\n"
            + "Example: " + COMMAND_WORD + " 1 "
            + PREFIX_PHONE + "91234567 "
            + PREFIX_EMAIL + "johndoe@example.com";

    public static final String MESSAGE_EDIT_PERSON_SUCCESS = "%1$s\nEdited Person: %2$s";
    public static final String MESSAGE_NOT_EDITED = "At least one field to edit must be provided.";
    public static final String MESSAGE_DUPLICATE_PHONE_AND_EMAIL =
            "This email and this phone number already exist in the address book.";
    public static final String MESSAGE_DUPLICATE_PHONE_NUMBER = "This phone number already exists in the address book";
    public static final String MESSAGE_DUPLICATE_EMAIL = "This email already exists in the address book.";
    public static final String MESSAGE_INVALID_VALUES = "Edit failed due to invalid values provided: %1$s";
    private final Index index;
    private final EditPersonDescriptor editPersonDescriptor;

    /**
     * @param index of the person in the filtered person list to edit
     * @param editPersonDescriptor details to edit the person with
     */
    public EditCommand(Index index, EditPersonDescriptor editPersonDescriptor) {
        requireNonNull(index);
        requireNonNull(editPersonDescriptor);

        this.index = index;
        this.editPersonDescriptor = new EditPersonDescriptor(editPersonDescriptor);
    }

    @Override
    public CommandResult execute(Model model) throws CommandException {
        requireNonNull(model);
        List<Person> lastShownList = model.getFilteredPersonList();

        if (index.getZeroBased() >= lastShownList.size()) {
            throw new CommandException(String.format(Messages.MESSAGE_PERSON_INDEX_OUT_OF_BOUND, index.getOneBased()));
        }

        Person personToEdit = lastShownList.get(index.getZeroBased());
        Person editedPerson = createEditedPerson(personToEdit, editPersonDescriptor);

        boolean phoneExists = model.hasPhone(editedPerson);
        boolean emailExists = model.hasEmail(editedPerson);

        if (!personToEdit.isSamePerson(editedPerson) && phoneExists && emailExists) {
            throw new CommandException(MESSAGE_DUPLICATE_PHONE_AND_EMAIL);
        } else if (!personToEdit.isPhonePresentAndSame(editedPerson) && phoneExists) {
            throw new CommandException(MESSAGE_DUPLICATE_PHONE_NUMBER);
        } else if (!personToEdit.isEmailPresentAndSame(editedPerson) && emailExists) {
            throw new CommandException(MESSAGE_DUPLICATE_EMAIL);
        }

        model.setPerson(personToEdit, editedPerson);
        model.updateFilteredPersonList(PREDICATE_SHOW_ALL_PERSONS);

        String changesDescription = getChangesDescription(personToEdit, editedPerson);

        return new CommandResult(
                String.format(MESSAGE_EDIT_PERSON_SUCCESS, changesDescription, Messages.format(editedPerson)));
    }

    /**
     * Creates and returns a {@code Person} with the details of {@code personToEdit}
     * edited with {@code editPersonDescriptor}.
     * @throws CommandException if there is an error in creating the edited person.
     */
    private static Person createEditedPerson(Person personToEdit, EditPersonDescriptor editPersonDescriptor)
            throws CommandException {
        assert personToEdit != null : "PersonToEdit should not be null";

        Name updatedName = editPersonDescriptor.getName().orElse(personToEdit.getName());
        Phone updatedPhone = editPersonDescriptor.getPhone().or(personToEdit :: getPhone).orElse(null);
        Email updatedEmail = editPersonDescriptor.getEmail().or(personToEdit :: getEmail).orElse(null);
        Address updatedAddress = editPersonDescriptor.getAddress().or(personToEdit :: getAddress).orElse(null);
        Set<Tag> updatedTags = editPersonDescriptor.getTags().orElse(personToEdit.getTags());

        ModuleRoleMap updatedModuleRoleMap;

        Description updatedDescription = editPersonDescriptor.getDescription()
            .or(personToEdit::getDescription).orElse(null);
        try {
            // The following code intentionally avoids the use of functional programming.
            // Reason being Optional.map() does not allow throwing checked exceptions.
            Optional<EditModuleRoleOperation> editModuleRoleOperation = editPersonDescriptor.getModuleRoleOperation();
            if (editModuleRoleOperation.isPresent()) {
                updatedModuleRoleMap = editModuleRoleOperation.get().execute(personToEdit.getModuleRoleMap());
            } else {
                updatedModuleRoleMap = personToEdit.getModuleRoleMap();
            }
        } catch (CommandException e) {
            throw new CommandException(String.format(MESSAGE_INVALID_VALUES, "\n" + e.getMessage())); // Re-throw
        }

        return new Person(updatedName, Optional.ofNullable(updatedPhone), Optional.ofNullable(updatedEmail),
                Optional.ofNullable(updatedAddress),
                updatedTags, updatedModuleRoleMap, Optional.ofNullable(updatedDescription));
    }

    /**
     * Returns a description of the changes made to the person.
     */
    public static String getChangesDescription(Person personBefore, Person personAfter) {
        StringBuilder changesDescription = new StringBuilder("Change(s) made: \n");
        boolean isChanged = false;
        if (!personBefore.getName().equals(personAfter.getName())) {
            isChanged = true;
            changesDescription.append("Name: ").append(personBefore.getName()).append(" -> ")
                    .append(personAfter.getName()).append("\n");
        }
        if (!personBefore.getPhone().equals(personAfter.getPhone())) {
            isChanged = true;
            changesDescription.append("Phone: ")
                    .append(personBefore.getPhone().map(Object::toString).orElse("<no phone>")).append(" -> ")
                    .append(personAfter.getPhone().map(Object::toString).orElse("<no phone>")).append("\n");
        }
        if (!personBefore.getEmail().equals(personAfter.getEmail())) {
            isChanged = true;
            changesDescription.append("Email: ")
                    .append(personBefore.getEmail().map(Object::toString).orElse("<no email>")).append(" -> ")
                    .append(personAfter.getEmail().map(Object::toString).orElse("<no email>")).append("\n");
        }
        if (!personBefore.getAddress().equals(personAfter.getAddress())) {
            isChanged = true;
            changesDescription.append("Address: ")
                    .append(personBefore.getAddress().map(Object::toString).orElse("<no address>")).append(" -> ")
                    .append(personAfter.getAddress().map(Object::toString).orElse("<no address>")).append("\n");
        }
        if (!personBefore.getTags().equals(personAfter.getTags())) {
            isChanged = true;
            changesDescription.append("Tags: ").append(personBefore.getTags()).append(" -> ")
                    .append(personAfter.getTags()).append("\n");
        }
        if (!personBefore.getModuleRoleMap().equals(personAfter.getModuleRoleMap())) {
            isChanged = true;
            changesDescription.append(EditModuleRoleOperation.getModuleCodeChangesDescription(
                    personBefore.getModuleRoleMap(), personAfter.getModuleRoleMap())).append("\n");
        }
        if (!personBefore.getDescriptionString().equals(personAfter.getDescriptionString())) {
            isChanged = true;
            changesDescription.append("Description: ")
                    .append(personBefore.getDescriptionString())
                    .append(" -> ")
                    .append(personAfter.getDescriptionString())
                    .append("\n");
        }

        return isChanged ? changesDescription.toString() : "No changes made.";
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        // instanceof handles nulls
        if (!(other instanceof EditCommand)) {
            return false;
        }

        EditCommand otherEditCommand = (EditCommand) other;
        return index.equals(otherEditCommand.index)
                && editPersonDescriptor.equals(otherEditCommand.editPersonDescriptor);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .add("index", index)
                .add("editPersonDescriptor", editPersonDescriptor)
                .toString();
    }

    /**
     * Stores the details to edit the person with. Each non-empty field value will replace the
     * corresponding field value of the person.
     */
    public static class EditPersonDescriptor {
        private Name name;
        private Phone phone;
        private Email email;
        private Address address;
        private Set<Tag> tags;
        private EditModuleRoleOperation editModuleRoleOperation;
        private Description description;

        public EditPersonDescriptor() {}

        /**
         * Copy constructor.
         * A defensive copy of {@code tags} is used internally.
         */
        public EditPersonDescriptor(EditPersonDescriptor toCopy) {
            setName(toCopy.name);
            setPhone(toCopy.phone);
            setEmail(toCopy.email);
            setAddress(toCopy.address);
            setTags(toCopy.tags);
            setModuleRoleOperation(toCopy.editModuleRoleOperation);
            setDescription(toCopy.description);
        }

        /**
         * Returns true if at least one field is edited.
         */
        public boolean isAnyFieldEdited() {
            return CollectionUtil.isAnyNonNull(name, phone, email, address, tags, editModuleRoleOperation, description);
        }

        public void setName(Name name) {
            this.name = name;
        }

        public Optional<Name> getName() {
            return Optional.ofNullable(name);
        }

        public void setPhone(Phone phone) {
            this.phone = phone;
        }

        public Optional<Phone> getPhone() {
            return Optional.ofNullable(phone);
        }

        public void setEmail(Email email) {
            this.email = email;
        }

        public Optional<Email> getEmail() {
            return Optional.ofNullable(email);
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public Optional<Address> getAddress() {
            return Optional.ofNullable(address);
        }

        /**
         * Sets {@code tags} to this object's {@code tags}.
         * A defensive copy of {@code tags} is used internally.
         */
        public void setTags(Set<Tag> tags) {
            this.tags = (tags != null) ? new HashSet<>(tags) : null;
        }

        /**
         * Returns an unmodifiable tag set, which throws {@code UnsupportedOperationException}
         * if modification is attempted.
         * Returns {@code Optional#empty()} if {@code tags} is null.
         */
        public Optional<Set<Tag>> getTags() {
            return (tags != null) ? Optional.of(Collections.unmodifiableSet(tags)) : Optional.empty();
        }

        public void setModuleRoleOperation(EditModuleRoleOperation editModuleRoleOperation) {
            this.editModuleRoleOperation = editModuleRoleOperation;
        }

        public Optional<EditModuleRoleOperation> getModuleRoleOperation() {
            return Optional.ofNullable(editModuleRoleOperation);
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public Optional<Description> getDescription() {
            return Optional.ofNullable(description);
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            // instanceof handles nulls
            if (!(other instanceof EditPersonDescriptor)) {
                return false;
            }

            EditPersonDescriptor otherEditPersonDescriptor = (EditPersonDescriptor) other;
            return Objects.equals(name, otherEditPersonDescriptor.name)
                    && Objects.equals(phone, otherEditPersonDescriptor.phone)
                    && Objects.equals(email, otherEditPersonDescriptor.email)
                    && Objects.equals(address, otherEditPersonDescriptor.address)
                    && Objects.equals(tags, otherEditPersonDescriptor.tags)
                    && Objects.equals(editModuleRoleOperation, otherEditPersonDescriptor.editModuleRoleOperation)
                    && Objects.equals(description, otherEditPersonDescriptor.description);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .add("name", name)
                    .add("phone", phone)
                    .add("email", email)
                    .add("address", address)
                    .add("tags", tags)
                    .add("editModuleRoleOperation", editModuleRoleOperation)
                    .add("description", description)
                    .toString();
        }
    }
}
