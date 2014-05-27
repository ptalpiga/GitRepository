
package oracle.mos.rest.sp;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;



@Path("1.0/ServiceRequests/srCreate")
public class SpSrCreateRestService extends SpRestBase {

  private static final ILogger LOGGER = Maf.getLogger(SpSrCreateRestService.class);

 
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/csis")
  public PagedResult<Csi> getCsis(@QueryParam("csiNumber") String csiNumber, 
                                  @QueryParam("csiName") String csiName, 
                                  @QueryParam("accountNumber") String accountNumber, 
                                  @QueryParam("accountName") String accountName, 
                                  @QueryParam("country") String country, 
                                  @QueryParam("contactEmail") String contactEmail, 
                                  @QueryParam("orderBy") String orderBy, 
                                  @QueryParam("offset") String offset, 
                                  @QueryParam("limit") String limit, 
                                  @QueryParam("totalResults") String totalResults) throws BusinessException, ServiceException {

    if (StringUtils.isEmpty(csiNumber) && StringUtils.isEmpty(csiName) && StringUtils.isEmpty(accountNumber) &&
        StringUtils.isEmpty(accountName) && StringUtils.isEmpty(country) && StringUtils.isEmpty(contactEmail)) {
      LOGGER.warning("Missing mandatory query param (csiNumber, csiName, accountNumber, accountName, country or contactEmail) was specified");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT,
                                  "At least one of csiNumber, csiName, accountNumber, accountName, country or contactEmail must be specified");
    }

    Csi filterFields = new Csi();
    filterFields.setCsiNumber(csiNumber);
    filterFields.setCsiName(csiName);
    filterFields.setAccountNumber(accountNumber);
    filterFields.setAccountName(accountName);
    filterFields.setCountry(country);
    filterFields.setContactEmail(contactEmail);

    PagedFilter<Csi> filter =
      new PagedFilter<Csi>(filterFields, parseOffset(offset), parseLimit(limit), getSortCriteria(orderBy),
                           "true".equalsIgnoreCase(totalResults));

    PagedResult<Csi> result = null;
    try {
      result = SrServiceLocator.getSrCreateService().getCsis(filter);
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No csis returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }
}
