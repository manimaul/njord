import {useCallback, useEffect, useState} from "react";


export type Admin = {
    signature: AdminSignature;
    signatureEncoded: string;
}

export type AdminSignature = {
    date: string;
    signature: string;
    uuid: string;
    expirationDate: string;
}

function storeSignature(signature: Admin) {
    window.localStorage.setItem("signature", JSON.stringify(signature))
    callbacks.forEach(each => {
        each(signature)
    })
}

function clearSignature() {
    window.localStorage.removeItem("signature")
    callbacks.forEach(each => {
        each(null)
    })
}

function adminSignature(): Admin | null {
    let sig = window.localStorage.getItem("signature")
    if (sig) {
        return JSON.parse(sig)
    }
    return null
}

var callbacks = new Set<(admin: Admin | null) => void>()

export function useAdmin(): [Admin | null, () => void, () => void, () => void] {
    const [admin, setAdmin] = useState<Admin | null>(adminSignature());

    let cb = (admin: Admin | null) => {
        setAdmin(admin)
    }

    useEffect(() => {
        callbacks.add(cb)
        return () => {
            callbacks.delete(cb)
        }
    }, [])

    function saveAdmin(signature: Admin) {
        storeSignature(signature)
        setAdmin(signature)
    }

    const adminRequest = useCallback(async () => {
        let response = await fetch("/v1/admin")
        let adm = await response.json() as Admin
        saveAdmin(adm)
    }, []);

    const adminValid = useCallback(async () => {

        try {
            let response = await fetch(
                "/v1/admin/verify",
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify(admin?.signature)
                }
            )
            console.log(`admin verify response ${response}`)
            let adm = await response.json() as Admin
            saveAdmin(adm)
        } catch (e) {
            clearSignature()
            setAdmin(null)
        }
    }, [admin]);

    return [admin, async () => {
        // login
        await adminRequest()
    }, () => {
        // logout
        clearSignature()
        setAdmin(null)
    }, async () => {
        // validate
        await adminValid()
    }]
}