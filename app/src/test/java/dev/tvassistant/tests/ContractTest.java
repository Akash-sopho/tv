package dev.tvassistant.tests;
import org.junit.Test;
public final class ContractTest {
    @Test public void interpreterContracts() { CoreSpec.main(new String[0]); }
    @Test public void catalogContracts() throws Exception { CatalogSpec.main(new String[0]); }
}
