package com.colingodsey.scalaminer.hashing

import com.colingodsey.Sha256
import com.colingodsey.scalaminer.network.Stratum.{ExtraNonce, MiningJob}
import com.colingodsey.scalaminer.network.Stratum
import com.colingodsey.scalaminer.utils._
import scala.concurrent.duration.Deadline
import javax.xml.bind.DatatypeConverter
import com.colingodsey.scalaminer.{ScalaMiner, Work}
import akka.util.ByteString

object Hashing {

	//returns little-endian
	def calculateMidstate(header: Seq[Byte], state: Option[Seq[Byte]] = None,
			rounds: Option[Int] = None) = {
		require(state == None)
		require(rounds == None)

		val sha256 = new ScalaSha256

		require(header.length == 64)

		//val ints = getInts(reverseEndian(header))

		sha256.update(reverseEndian(header))//reverseEndian(header).toArray)
		val midState = sha256.getState.toStream.flatMap(intToBytes)

		reverseEndian(midState)
	}

	/*
	def difMask = BigInt({
		val hex = if(isScrypt)
			"0000ffff00000000000000000000000000000000000000000000000000000000"
		else
			"00000000ffff0000000000000000000000000000000000000000000000000000"

		DatatypeConverter.parseHexBinary(hex)
	})
	 */

	def getWork(hashType: ScalaMiner.HashType, extraNonceInt: Int, job: MiningJob,
			extraNonceInfo: ExtraNonce, targetBytes: Seq[Byte], needsMidstate: Boolean) = {
		val bytes = intToBytes(extraNonceInt)
		val enBytes = Seq.fill[Byte](extraNonceInfo.extranonce2Size -
				bytes.length)(0) ++ bytes

		val extraNonce = extraNonceInfo.extranonce1 ++ enBytes

		val sha256 = new ScalaSha256

		val coinbase = ScalaMiner.BufferType.empty ++
				job.coinbase1 ++ extraNonce ++ job.coinbase2

		val coinbaseHash = doubleHash(coinbase, sha256)

		val merkleRoot = reverseEndian(job.merkleBranches.foldLeft(coinbaseHash) { (a, b) =>
			doubleHash(a ++ b, sha256)
		})

		val ntime = (System.currentTimeMillis / 1000) + job.dTime

		//merkleJobMap += merkleRoot.toSeq -> workJob

		val serializedHeader = ScalaMiner.BufferType.empty ++
				job.protoVersion ++ job.previousHash ++ merkleRoot ++
				intToBytes(ntime.toInt) ++ job.nBits ++ intToBytes(0) ++ //enBytes ++
				"000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000".fromHex

		//require(serializedHeader.length == 128, "bad length " + serializedHeader.length)

		//TODO: this is the 'old' target?
		val target = ScalaMiner.BufferType.empty ++ (if(targetBytes.isEmpty)
			"ffffffffffffffffffffffffffffffffffffffffffffffffffffffff00000000".fromHex.toSeq
		else targetBytes)

		val midstate = if(needsMidstate) calculateMidstate(serializedHeader.take(64))
		else Nil

		Stratum.Job(Work(hashType, serializedHeader, midstate, target),
			job.id, merkleRoot, enBytes)
	}

	val bitcoinTarget1 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffff00000000"
	val bitcoinDefaultTarget =  BigInt({
		val hex = "00000000ffff0000000000000000000000000000000000000000000000000000"

		hex.fromHex.toArray
	})

	val scryptDefaultTarget =  BigInt({
		val hex = "0000ffff00000000000000000000000000000000000000000000000000000000"

		hex.fromHex.toArray
	})
}