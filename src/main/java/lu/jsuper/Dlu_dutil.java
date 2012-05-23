/*! @file Dlu_dutil.java
 * \brief Matrix utility functions
 *
 * <pre>
 * -- SuperLU routine (version 3.1) --
 * Univ. of California Berkeley, Xerox Palo Alto Research Center,
 * and Lawrence Berkeley National Lab.
 * August 1, 2008
 *
 * Copyright (c) 1994 by Xerox Corporation.  All rights reserved.
 *
 * THIS MATERIAL IS PROVIDED AS IS, WITH ABSOLUTELY NO WARRANTY
 * EXPRESSED OR IMPLIED.  ANY USE IS AT YOUR OWN RISK.
 *
 * Permission is hereby granted to use or copy this program for any
 * purpose, provided the above notices are retained on all copies.
 * Permission to modify the code and to distribute modified code is
 * granted, provided the above notices are retained, and a notice that
 * the code was modified is included with the above copyright notice.
 * </pre>
 */
package lu.jsuper;

import lu.jsuper.Dlu_supermatrix.DNformat;
import lu.jsuper.Dlu_supermatrix.Dtype_t;
import lu.jsuper.Dlu_supermatrix.Mtype_t;
import lu.jsuper.Dlu_supermatrix.Stype_t;
import lu.jsuper.Dlu_supermatrix.SuperMatrix;
import lu.jsuper.Dlu_supermatrix.NCformat;

public class Dlu_dutil {

	public static SuperMatrix dCreate_CompCol_Matrix(int m, int n, int nnz,
		       double nzval[], int rowind[], int colptr[],
		       Stype_t stype, Dtype_t dtype, Mtype_t mtype) {
		NCformat Astore;

		SuperMatrix A = new SuperMatrix();

	    A.Stype = stype;
	    A.Dtype = dtype;
	    A.Mtype = mtype;
	    A.nrow = m;
	    A.ncol = n;
	    A.Store = new NCformat();
	    Astore = (NCformat) A.Store;
	    Astore.nnz = nnz;
	    Astore.nzval = nzval;
	    Astore.rowind = rowind;
	    Astore.colptr = colptr;

	    return A;
	}

	public static SuperMatrix dCreate_Dense_Matrix(int m, int n, double x[],
			int ldx, Stype_t stype, Dtype_t dtype, Mtype_t mtype) {
	    DNformat Xstore;

	    SuperMatrix X = new SuperMatrix();

	    X.Stype = stype;
	    X.Dtype = dtype;
	    X.Mtype = mtype;
	    X.nrow = m;
	    X.ncol = n;
	    X.Store = new DNformat();
	    Xstore = (DNformat) X.Store;
	    Xstore.lda = ldx;
	    Xstore.nzval = x;

	    return X;
	}

	/**! \brief Fills a double precision array with a given value.
	 */
	public static void dfill(double a[], int alen, double dval)
	{
	    int i;
	    for (i = 0; i < alen; i++) a[i] = dval;
	}

}