package couch;

import com.google.gson.JsonObject;
import com.omr.exceptions.MappingNotCorrect;

public class Seq {
	private String 	_id, _rev, StudentName, group, collection, qid, created_at,modified_at,parent_id;
	private JsonObject qSeq, optSeq;
	
	public String getParentId()
	{
		return parent_id;
	}
	public String getStudentName(){
		return StudentName;
	}
	public String get_id(){
		return _id;
	}
	public String get_rev(){
		return _rev;
	}
	public String getgroup(){
		return group;
	}
	public String getcollection(){
		return collection;
	}
	public String getqid(){
		return qid;
	}
	public String getcreated_at(){
		return created_at;
	}
	public String getmodified_at(){
		return modified_at;
	}
	public JsonObject getqSeq(){
		return qSeq;
	}
	public String getqnameat(int i){
		return qSeq.get(""+i).getAsString();
	}
	public JsonObject getoptSeq(){
		return optSeq;
	}
	public JsonObject toJson(){
		JsonObject json = new JsonObject();
		json.addProperty("_id", get_id());
		json.addProperty("_rev", get_rev());
		json.addProperty("StudentName", getStudentName());
		json.addProperty("collection", getcollection());
		json.addProperty("qid", getqid());
		json.addProperty("created_at", getcreated_at());
		json.addProperty("modified_at", getmodified_at());
		json.add("qSeq", qSeq);
		json.add("optSeq",optSeq);
		return json;
	}
	public void validate() throws MappingNotCorrect {
		if(_id == null || StudentName == null || collection == null || qid == null || qSeq == null || optSeq == null) throw new MappingNotCorrect();
		
	}
	public boolean CheckIfOptSequenceIsNull()
	{
		if(optSeq==null)
			return true;
		else
			return false;
	}
	public boolean CheckIfParentIdExists()
	{
		if(parent_id.contains("null"))
			return true;
		else
			return false;
	}
}
