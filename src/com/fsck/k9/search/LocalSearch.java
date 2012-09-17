package com.fsck.k9.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.os.Parcel;
import android.os.Parcelable;

import com.fsck.k9.mail.Flag;

/**
 * This class represents a local search. 
 * 
 * To be able to find all the folder and flag conditions the tree adheres 
 * to some positioning rules:
 *      - All and only forbidden flag conditions are childeren of the forbiddenFlagNode 
 *      - All and only required flag conditions are childeren of the requiredFlagNode 
 *      - All and only folder conditions are childeren of the foldersNode 
 *      
 * There can still be conditions about flags & folders in a combination with other fields,
 * those don't have to be childeren of those nodes. The rules only apply for "level-0" conditions.
 * 
 * Removing conditions is done through matching there unique id in the leafset and then
 * removing them from the tree.
 * 
 * @author dzan
 * 
 * TODO conflicting conditions check
 *         - forbidden folders & forbidden folders subtree
 * TODO assign each node a unique id that's used to retrieve it from the leaveset and remove.
 */

public class LocalSearch implements SearchSpecification {

    private String mName;
	private boolean mPredefined;	
	
    private HashSet<String> mAccountUuids = new HashSet<String>();   
    private ConditionsTreeNode mConditions = null;  
    private HashSet<ConditionsTreeNode> mLeafSet = new HashSet<ConditionsTreeNode>();
    
    public LocalSearch(String name) {
        this.mName = name;
    }

    /**
     * Use this only if the search won't be saved. Saved searches need 
     * a name!
     */
    public LocalSearch(){}
    
    /**
     * Use this constructor when you know what you'r doing. Normally it's only used 
     * when restoring these search objects from the database.
     * 
     * @param name Name of the search
     * @param searchConditions SearchConditions, may contains flags and folders
     * @param accounts Relative Account's uuid's
     * @param predefined Is this a predefined search or a user created one?
     */
    protected LocalSearch(String name, ConditionsTreeNode searchConditions, 
    		String accounts, boolean predefined) {
    	this(name);
    	mConditions = searchConditions;
    	mPredefined = predefined;
    	mLeafSet = new HashSet<ConditionsTreeNode>();
    	if (mConditions != null) {
    		mLeafSet.addAll(mConditions.getLeafSet());
    	}
    	
    	// initialize accounts
    	if (accounts != null) {
	    	for (String account : accounts.split(",")) {
	    		mAccountUuids.add(account);
	    	}
    	} else {
    		// impossible but still not unrecoverable
    	}
    }
    
    /* ********************************************************************
     *  Parcelable
     * ********************************************************************/
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mName);
		dest.writeByte((byte) (mPredefined ? 1 : 0));
		dest.writeStringList(new ArrayList<String>(mAccountUuids));
		dest.writeParcelable(mConditions, flags);
	}
	
	public static final Parcelable.Creator<LocalSearch> CREATOR
	    = new Parcelable.Creator<LocalSearch>() {
	    public LocalSearch createFromParcel(Parcel in) {
	        return new LocalSearch(in);
	    }
	
	    public LocalSearch[] newArray(int size) {
	        return new LocalSearch[size];
	    }
	};
	
	public LocalSearch(Parcel in) {
		mName = in.readString();
		mPredefined = in.readByte() == 1;
		mAccountUuids.addAll(in.createStringArrayList());	
		mConditions = in.readParcelable(LocalSearch.class.getClassLoader());
		mLeafSet = mConditions.getLeafSet();
	}
	
    /* ********************************************************************
     *  Public Manipulation Methods
     * ********************************************************************/

    /**
     * Sets the name of the saved search. If one existed it will
     * be overwritten.
     *
     * @param name Name to be set.
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * Add a new account to the search. When no accounts are 
     * added manually we search all accounts on the device.
     * 
     * @param uuid Uuid of the account to be added.
     */
    public void addAccountUuid(String uuid) {
    	mAccountUuids.add(uuid);
    }

    /**
     * Removes an account UUID from the current search.
     * 
     * @param uuid Account UUID to remove.
     * @return True if removed, false otherwise.
     */
    public boolean removeAccountUuid(String uuid) {
    	return mAccountUuids.remove(uuid);
    }
    
	/**
	 * Adds the provided condition as the second argument of an AND 
	 * clause to this node.
	 * 
	 * @param condition Condition to 'AND' with.
	 * @return New top AND node, new root.
	 */
	public ConditionsTreeNode and(SearchCondition condition) {
		try {
			ConditionsTreeNode tmp = new ConditionsTreeNode(condition);
			mLeafSet.add(tmp);
			
			if (mConditions == null) {
				mConditions = tmp;
				return tmp;
			}
			
			mConditions = mConditions.and(tmp);
			return mConditions;
		} catch (Exception e) {
			// IMPOSSIBLE!
			return null;
		}
	}
	
	/**
	 * Adds the provided condition as the second argument of an OR 
	 * clause to this node.
	 * 
	 * @param condition Condition to 'OR' with.
	 * @return New top OR node, new root.
	 */
	public ConditionsTreeNode or(SearchCondition condition) {
		try {
			ConditionsTreeNode tmp = new ConditionsTreeNode(condition);
			mLeafSet.add(tmp);
			
			if (mConditions == null) {
				mConditions = tmp;
				return tmp;
			}
			
			mConditions = mConditions.or(tmp);
			return mConditions;
		} catch (Exception e) {
			// IMPOSSIBLE!
			return null;
		}
	}
	
	public void allRequiredFlags(Flag[] requiredFlags) {
		for (Flag f : requiredFlags) {
			and(new SearchCondition(SEARCHFIELD.FLAG, ATTRIBUTE.EQUALS, f.name()));
		}			
	}
	
	public void allForbiddenFlags(Flag[] forbiddenFlags) {
		for (Flag f : forbiddenFlags) {
			and(new SearchCondition(SEARCHFIELD.FLAG, ATTRIBUTE.NOT_EQUALS, f.name()));
		}		
	}
	
	public void addAllowedFolder(String name) {
		// TODO find folder sub-tree
		or(new SearchCondition(SEARCHFIELD.FOLDER, ATTRIBUTE.EQUALS, name));
	}
	
	/*
	 * TODO make this more advanced!
	 * This is a temporarely solution that does NOT WORK for
	 * real searches.
	 */
	public List<String> getFolderNames() {
		ArrayList<String> results = new ArrayList<String>();
		for (ConditionsTreeNode node : mLeafSet) {
			if (node.mCondition.field == SEARCHFIELD.FOLDER
					&& node.mCondition.attribute == ATTRIBUTE.EQUALS) {
				results.add(node.mCondition.value);
			}
		}
		return results;
	}
	
	public Set<ConditionsTreeNode> getLeafSet() {
		return mLeafSet;
	}
	
	/**
	 * Adds a new condition to 
	 * @param subject
	 * @param string
	 * @param contains
	 * @throws IllegalConditionException
	 */
	public void addCondition(SEARCHFIELD subject, String string,
			ATTRIBUTE contains) throws IllegalConditionException {
		if (subject == SEARCHFIELD.FLAG || subject == SEARCHFIELD.FOLDER) {
			throw new IllegalConditionException("Use the specific methods to flag/folder related conditions.");
		}
		
		
	}
    /* ********************************************************************
     *  Public Information Methods
     * ********************************************************************/
    /**
     * Returns the name of the saved search.
     *
     * @return Name of the search.
     */
    public String getName() {
        return mName;
    }
	
	public boolean isPredefined() {
		return mPredefined;
	}
	
    /* ********************************************************************
     *  SearchSpecification Interface Methods
     * ********************************************************************/

    @Override
    public String[] getAccountUuids() {
        if (mAccountUuids.size() == 0) {
            return new String[] {SearchSpecification.ALL_ACCOUNTS};
        }

        String[] tmp = new String[mAccountUuids.size()];
        mAccountUuids.toArray(tmp);
        return tmp;
    }

	@Override
	public ConditionsTreeNode getConditions() {
		return mConditions;
	}
}