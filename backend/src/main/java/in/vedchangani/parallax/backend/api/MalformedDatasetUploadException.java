package in.vedchangani.parallax.backend.api;

public final class MalformedDatasetUploadException extends RuntimeException {

    private final String field;

    public MalformedDatasetUploadException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
