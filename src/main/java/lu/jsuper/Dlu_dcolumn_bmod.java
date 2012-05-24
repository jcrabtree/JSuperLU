/*! @file dcolumn_bmod.c
 *  \brief performs numeric block updates
 *
 * <pre>
 * -- SuperLU routine (version 3.0) --
 * Univ. of California Berkeley, Xerox Palo Alto Research Center,
 * and Lawrence Berkeley National Lab.
 * October 15, 2003
 *
 * Copyright (c) 1994 by Xerox Corporation.  All rights reserved.
 *
 * THIS MATERIAL IS PROVIDED AS IS, WITH ABSOLUTELY NO WARRANTY
 * EXPRESSED OR IMPLIED.  ANY USE IS AT YOUR OWN RISK.
 *
 *  Permission is hereby granted to use or copy this program for any
 *  purpose, provided the above notices are retained on all copies.
 *  Permission to modify the code and to distribute modified code is
 *  granted, provided the above notices are retained, and a notice that
 *  the code was modified is included with the above copyright notice.
 * </pre>
*/
package lu.jsuper;

import org.netlib.blas.BLAS;

import lu.jsuper.Dlu_slu_ddefs.GlobalLU_t;
import lu.jsuper.Dlu_slu_util.SuperLUStat_t;
import lu.jsuper.Dlu_superlu_enum_consts.MemType;
import lu.jsuper.Dlu_superlu_enum_consts.PhaseType;

import static lu.jsuper.Dlu_slu_util.SUPERLU_MAX;
import static lu.jsuper.Dlu_util.USE_VENDOR_BLAS;
import static lu.jsuper.Dlu_dmyblas2.dlsolve;
import static lu.jsuper.Dlu_dmyblas2.dmatvec;
import static lu.jsuper.Dlu_dmemory.dLUMemXpand;


public class Dlu_dcolumn_bmod {

	/**! \brief
	 *
	 * <pre>
	 * Purpose:
	 * ========
	 * Performs numeric block updates (sup-col) in topological order.
	 * It features: col-col, 2cols-col, 3cols-col, and sup-col updates.
	 * Special processing on the supernodal portion of L\U[*,j]
	 * Return value:   0 - successful return
	 *               > 0 - number of bytes allocated when run out of space
	 * </pre>
	 */
	public static int
	dcolumn_bmod (
		     final int  jcol,	  /* in */
		     final int  nseg,	  /* in */
		     double     dense[],	  /* in */
		     double     tempv[],	  /* working array */
		     int        segrep[],  /* in */
		     int        repfnz[],  /* in */
		     int        fpanelc,  /* in -- first column in the current panel */
		     GlobalLU_t Glu,     /* modified */
		     SuperLUStat_t stat  /* output */
		     )
	{
	    int         incx = 1, incy = 1;
	    double      alpha, beta;

	    /* krep = representative of current k-th supernode
	     * fsupc = first supernodal column
	     * nsupc = no of columns in supernode
	     * nsupr = no of rows in supernode (used as leading dimension)
	     * luptr = location of supernodal LU-block in storage
	     * kfnz = first nonz in the k-th supernodal segment
	     * no_zeros = no of leading zeros in a supernodal U-segment
	     */
	    double       ukj, ukj1, ukj2;
	    int          luptr, luptr1, luptr2;
	    int          fsupc, nsupc, nsupr, segsze;
	    int          nrow;	  /* No of rows in the matrix of matrix-vector */
	    int          jcolp1, jsupno, k, ksub, krep, krep_ind, ksupno;
	    int          lptr, kfnz, isub, irow, i;
	    int          no_zeros, new_next;
	    int          ufirst, nextlu;
	    int          fst_col; /* First column within small LU update */
	    int          d_fsupc; /* Distance between the first column of the current
				     panel and the first column of the current snode. */
	    int          xsup[], supno[];
	    int          lsub[], xlsub[];
	    double       lusup[];
	    int          xlusup[];
	    int[]        nzlumax = new int[0];
	    double       tempv1[];
	    int          tempv1_offset;
	    double       zero = 0.0;
	    double       one = 1.0;
	    double       none = -1.0;
	    int          mem_error;
	    float        ops[] = stat.ops;

	    xsup    = Glu.xsup;
	    supno   = Glu.supno;
	    lsub    = Glu.lsub;
	    xlsub   = Glu.xlsub;
	    lusup   = Glu.lusup;
	    xlusup  = Glu.xlusup;
	    nzlumax[0] = Glu.nzlumax;
	    jcolp1 = jcol + 1;
	    jsupno = supno[jcol];

	    /*
	     * For each nonz supernode segment of U[*,j] in topological order
	     */
	    k = nseg - 1;
	    for (ksub = 0; ksub < nseg; ksub++) {

		krep = segrep[k];
		k--;
		ksupno = supno[krep];
		if ( jsupno != ksupno ) { /* Outside the rectangular supernode */

		    fsupc = xsup[ksupno];
		    fst_col = SUPERLU_MAX ( fsupc, fpanelc );

	  	    /* Distance from the current supernode to the current panel;
		       d_fsupc=0 if fsupc > fpanelc. */
	  	    d_fsupc = fst_col - fsupc;

		    luptr = xlusup[fst_col] + d_fsupc;
		    lptr = xlsub[fsupc] + d_fsupc;

		    kfnz = repfnz[krep];
		    kfnz = SUPERLU_MAX ( kfnz, fpanelc );

		    segsze = krep - kfnz + 1;
		    nsupc = krep - fst_col + 1;
		    nsupr = xlsub[fsupc+1] - xlsub[fsupc];	/* Leading dimension */
		    nrow = nsupr - d_fsupc - nsupc;
		    krep_ind = lptr + nsupc - 1;

		    ops[PhaseType.TRSV.ordinal()] += segsze * (segsze - 1);
		    ops[PhaseType.GEMV.ordinal()] += 2 * nrow * segsze;


		    /*
		     * Case 1: Update U-segment of size 1 -- col-col update
		     */
		    if ( segsze == 1 ) {
		  	ukj = dense[lsub[krep_ind]];
			luptr += nsupr*(nsupc-1) + nsupc;

			for (i = lptr + nsupc; i < xlsub[fsupc+1]; ++i) {
			    irow = lsub[i];
			    dense[irow] -=  ukj*lusup[luptr];
			    luptr++;
			}

		    } else if ( segsze <= 3 ) {
			ukj = dense[lsub[krep_ind]];
			luptr += nsupr*(nsupc-1) + nsupc-1;
			ukj1 = dense[lsub[krep_ind - 1]];
			luptr1 = luptr - nsupr;

			if ( segsze == 2 ) { /* Case 2: 2cols-col update */
			    ukj -= ukj1 * lusup[luptr1];
			    dense[lsub[krep_ind]] = ukj;
			    for (i = lptr + nsupc; i < xlsub[fsupc+1]; ++i) {
			    	irow = lsub[i];
			    	luptr++;
			    	luptr1++;
			    	dense[irow] -= ( ukj*lusup[luptr]
						+ ukj1*lusup[luptr1] );
			    }
			} else { /* Case 3: 3cols-col update */
			    ukj2 = dense[lsub[krep_ind - 2]];
			    luptr2 = luptr1 - nsupr;
			    ukj1 -= ukj2 * lusup[luptr2-1];
			    ukj = ukj - ukj1*lusup[luptr1] - ukj2*lusup[luptr2];
			    dense[lsub[krep_ind]] = ukj;
			    dense[lsub[krep_ind-1]] = ukj1;
			    for (i = lptr + nsupc; i < xlsub[fsupc+1]; ++i) {
			    	irow = lsub[i];
			    	luptr++;
			    	luptr1++;
				luptr2++;
			    	dense[irow] -= ( ukj*lusup[luptr]
				     + ukj1*lusup[luptr1] + ukj2*lusup[luptr2] );
			    }
			}



		    } else {
		  	/*
			 * Case: sup-col update
			 * Perform a triangular solve and block update,
			 * then scatter the result of sup-col update to dense
			 */

			no_zeros = kfnz - fst_col;

		        /* Copy U[*,j] segment from dense[*] to tempv[*] */
		        isub = lptr + no_zeros;
		        for (i = 0; i < segsze; i++) {
		  	    irow = lsub[isub];
			    tempv[i] = dense[irow];
			    ++isub;
		        }

		        /* Dense triangular solve -- start effective triangle */
			luptr += nsupr * no_zeros + no_zeros;

			if (USE_VENDOR_BLAS) {
			BLAS blas = BLAS.getInstance();
			blas.dtrsv( "L", "N", "U", segsze, lusup[luptr],
			       nsupr, tempv, incx );
	 		luptr += segsze;  /* Dense matrix-vector */
			tempv1 = tempv;
			tempv1_offset = segsze;
            alpha = one;
            beta = zero;
			blas.dgemv( "N", &nrow, &segsze, &alpha, &lusup[luptr],
			       &nsupr, tempv, &incx, &beta, tempv1, &incy );
			} else {
			dlsolve ( nsupr, segsze, &lusup[luptr], tempv );

	 		luptr += segsze;  /* Dense matrix-vector */
			tempv1 = tempv;
			tempv1_offset = segsze;
			dmatvec (nsupr, nrow , segsze, lusup[luptr], tempv, tempv1);
			}


	                /* Scatter tempv[] into SPA dense[] as a temporary storage */
	                isub = lptr + no_zeros;
	                for (i = 0; i < segsze; i++) {
	                    irow = lsub[isub];
	                    dense[irow] = tempv[i];
	                    tempv[i] = zero;
	                    ++isub;
	                }

			/* Scatter tempv1[] into SPA dense[] */
			for (i = 0; i < nrow; i++) {
			    irow = lsub[isub];
			    dense[irow] -= tempv1[tempv1_offset+i];
			    tempv1[tempv1_offset+i] = zero;
			    ++isub;
			}
		    }

		} /* if jsupno ... */

	    } /* for each segment... */

	    /*
	     *	Process the supernodal portion of L\U[*,j]
	     */
	    nextlu = xlusup[jcol];
	    fsupc = xsup[jsupno];

	    /* Copy the SPA dense into L\U[*,j] */
	    new_next = nextlu + xlsub[fsupc+1] - xlsub[fsupc];
	    while ( new_next > nzlumax[0] ) {
		if ((mem_error = dLUMemXpand(jcol, nextlu, MemType.LUSUP, nzlumax, Glu)) != 0)
		    return (mem_error);
		lusup = Glu.lusup;
		lsub = Glu.lsub;
	    }

	    for (isub = xlsub[fsupc]; isub < xlsub[fsupc+1]; isub++) {
	  	irow = lsub[isub];
		lusup[nextlu] = dense[irow];
	        dense[irow] = zero;
		++nextlu;
	    }

	    xlusup[jcolp1] = nextlu;	/* Close L\U[*,jcol] */

	    /* For more updates within the panel (also within the current supernode),
	     * should start from the first column of the panel, or the first column
	     * of the supernode, whichever is bigger. There are 2 cases:
	     *    1) fsupc < fpanelc, then fst_col := fpanelc
	     *    2) fsupc >= fpanelc, then fst_col := fsupc
	     */
	    fst_col = SUPERLU_MAX ( fsupc, fpanelc );

	    if ( fst_col < jcol ) {

	  	/* Distance between the current supernode and the current panel.
		   d_fsupc=0 if fsupc >= fpanelc. */
	  	d_fsupc = fst_col - fsupc;

		lptr = xlsub[fsupc] + d_fsupc;
		luptr = xlusup[fst_col] + d_fsupc;
		nsupr = xlsub[fsupc+1] - xlsub[fsupc];	/* Leading dimension */
		nsupc = jcol - fst_col;	/* Excluding jcol */
		nrow = nsupr - d_fsupc - nsupc;

		/* Points to the beginning of jcol in snode L\U(jsupno) */
		ufirst = xlusup[jcol] + d_fsupc;

		ops[PhaseType.TRSV.ordinal()] += nsupc * (nsupc - 1);
		ops[PhaseType.GEMV.ordinal()] += 2 * nrow * nsupc;

		if (USE_VENDOR_BLAS) {
		BLAS blas = BLAS.getInstance();
		blas.dtrsv( "L", "N", "U", &nsupc, &lusup[luptr],
		       &nsupr, &lusup[ufirst], &incx );

		alpha = none; beta = one; /* y := beta*y + alpha*A*x */

		blas.dgemv( "N", &nrow, &nsupc, &alpha, &lusup[luptr+nsupc], &nsupr,
		       &lusup[ufirst], &incx, &beta, &lusup[ufirst+nsupc], &incy );
		}
		else
		{
		dlsolve ( nsupr, nsupc, &lusup[luptr], &lusup[ufirst] );

		dmatvec ( nsupr, nrow, nsupc, &lusup[luptr+nsupc],
			&lusup[ufirst], tempv );

	        /* Copy updates from tempv[*] into lusup[*] */
		isub = ufirst + nsupc;
		for (i = 0; i < nrow; i++) {
		    lusup[isub] -= tempv[i];
		    tempv[i] = 0.0;
		    ++isub;
		}

		}


	    } /* if fst_col < jcol ... */

	    return 0;
	}

}
