package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatasetOwnershipIT {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerCanAccessOwnDatasetAndVersionAndBars() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        DatasetSummary dataset = datasetService.createDataset(a, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(a, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        assertEquals(dataset.id(), datasetService.getDataset(a, dataset.id()).id());
        assertEquals(1, datasetService.getVersion(a, dataset.id(), 1).versionNumber());
        assertEquals(2, datasetService.getVerifiedSeries(a, dataset.id(), 1).series().bars().size());
    }

    @Test
    void anotherOwnerCannotGetTheDataset() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        DatasetSummary dataset = datasetService.createDataset(a, DatasetFixtures.uniqueName(), "AAPL");

        assertThrows(DatasetNotFoundException.class, () -> datasetService.getDataset(b, dataset.id()));
    }

    @Test
    void anotherOwnerCannotCreateAVersionUnderTheDataset() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        DatasetSummary dataset = datasetService.createDataset(a, DatasetFixtures.uniqueName(), "AAPL");

        assertThrows(DatasetNotFoundException.class, () -> datasetService.createVersionFromCsv(b, dataset.id(),
                DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW, "b.csv"));
        assertEquals(0, datasetService.getDataset(a, dataset.id()).latestVersionNumber());
    }

    @Test
    void anotherOwnerCannotListVersions() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        DatasetSummary dataset = datasetService.createDataset(a, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(a, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        assertThrows(DatasetNotFoundException.class, () -> datasetService.listVersions(b, dataset.id()));
    }

    @Test
    void anotherOwnerCannotGetTheVersion() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        DatasetSummary dataset = datasetService.createDataset(a, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(a, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        assertThrows(DatasetVersionNotFoundException.class, () -> datasetService.getVersion(b, dataset.id(), 1));
    }

    @Test
    void anotherOwnerCannotGetTheBars() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        DatasetSummary dataset = datasetService.createDataset(a, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createVersionFromCsv(a, dataset.id(), DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");

        assertThrows(DatasetVersionNotFoundException.class,
                () -> datasetService.getVerifiedSeries(b, dataset.id(), 1));
    }

    @Test
    void listingDatasetsIsIsolatedPerOwner() {
        UserId a = TestUsers.create(jdbcTemplate, "owner-a");
        UserId b = TestUsers.create(jdbcTemplate, "owner-b");
        DatasetSummary aDataset = datasetService.createDataset(a, DatasetFixtures.uniqueName(), "AAPL");
        datasetService.createDataset(b, DatasetFixtures.uniqueName(), "MSFT");

        List<DatasetSummary> aDatasets = datasetService.listDatasets(a);
        assertEquals(1, aDatasets.size());
        assertEquals(aDataset.id(), aDatasets.get(0).id());
        assertTrue(datasetService.listDatasets(b).stream().noneMatch(d -> d.id() == aDataset.id()));
    }
}
